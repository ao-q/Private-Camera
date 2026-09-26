package com.example.camera.discovery

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.media.MediaRecorder
import android.os.Build
import android.util.Range
import android.util.Size
import android.util.SizeF
import com.example.camera.model.CameraInfoModel
import com.example.camera.model.LensFacingType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CameraDiscoveryManager(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    suspend fun discoverAllCameras(): List<CameraInfoModel> = withContext(Dispatchers.IO) {
        val discovered = mutableListOf<CameraInfoModel>()
        try {
            val cameraIds = cameraManager.cameraIdList
            if (cameraIds.isEmpty()) return@withContext emptyList()

            // Find reference back camera focal length to compute relative zoom factors
            var refBackFocalLength = 4.3f
            for (id in cameraIds) {
                try {
                    val chars = cameraManager.getCameraCharacteristics(id)
                    val facing = chars.get(CameraCharacteristics.LENS_FACING)
                    if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                        val focals = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                        if (focals != null && focals.isNotEmpty()) {
                            refBackFocalLength = focals[0]
                            break
                        }
                    }
                } catch (_: Exception) {}
            }

            for (id in cameraIds) {
                try {
                    val chars = cameraManager.getCameraCharacteristics(id)
                    val model = parseCameraCharacteristics(id, chars, refBackFocalLength)
                    discovered.add(model)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Sort so Back cameras come first (Ultrawide -> Main -> Telephoto), followed by Front
        discovered.sortedWith(
            compareBy<CameraInfoModel> {
                when (it.facing) {
                    LensFacingType.BACK -> 0
                    LensFacingType.FRONT -> 1
                    LensFacingType.EXTERNAL -> 2
                }
            }.thenBy { it.zoomFactor }
             .thenBy { it.cameraId }
        )
    }

    private fun parseCameraCharacteristics(
        cameraId: String,
        chars: CameraCharacteristics,
        refBackFocalLength: Float
    ): CameraInfoModel {
        val facingInt = chars.get(CameraCharacteristics.LENS_FACING) ?: CameraCharacteristics.LENS_FACING_BACK
        val facing = when (facingInt) {
            CameraCharacteristics.LENS_FACING_FRONT -> LensFacingType.FRONT
            CameraCharacteristics.LENS_FACING_BACK -> LensFacingType.BACK
            else -> LensFacingType.EXTERNAL
        }

        val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList() ?: listOf(4.0f)
        val primaryFocal = focalLengths.firstOrNull() ?: 4.0f
        val sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
        val flashAvailable = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false

        // RAW capability check
        val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
        val hasRawCap = capabilities.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW)

        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val rawSizes = if (map != null && hasRawCap) {
            map.getOutputSizes(ImageFormat.RAW_SENSOR)?.toList() ?: emptyList()
        } else {
            emptyList()
        }
        val supportsRaw = hasRawCap && rawSizes.isNotEmpty()

        // Multi-camera check
        val isLogicalMulti = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)
        } else {
            false
        }

        val physicalIds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            chars.physicalCameraIds ?: emptySet()
        } else {
            emptySet()
        }

        // Hardware Level
        val hwLevel = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
            ?: CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY
        val hwLevelName = when (hwLevel) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "Limited"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "Full"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "Legacy"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "Level 3 (Pro)"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "External"
            else -> "Unknown"
        }

        // Photo sizes (YUV_420_888 for lossless capture, fallback to JPEG/RAW)
        val yuvSizes = map?.getOutputSizes(ImageFormat.YUV_420_888)?.toList() ?: emptyList()
        val jpegSizes = map?.getOutputSizes(ImageFormat.JPEG)?.toList() ?: emptyList()
        val combinedPhotoSizes = if (yuvSizes.isNotEmpty()) yuvSizes else jpegSizes
        val sortedPhotoSizes = combinedPhotoSizes.sortedByDescending { it.width.toLong() * it.height.toLong() }
        val maxPhotoSize = sortedPhotoSizes.firstOrNull() ?: Size(1920, 1080)

        // Video sizes
        val videoOutputSizes = map?.getOutputSizes(MediaRecorder::class.java)?.toList() ?: emptyList()
        val sortedVideoSizes = videoOutputSizes.sortedByDescending { it.width.toLong() * it.height.toLong() }

        // FPS Ranges
        val fpsRanges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.toList() ?: listOf(Range(30, 30))

        // Stabilization
        val videoStabModes = chars.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES) ?: intArrayOf()
        val supportsVideoStab = videoStabModes.contains(CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON)
        val oisModes = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) ?: intArrayOf()
        val supportsOis = oisModes.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON)

        // Max Digital Zoom
        val maxZoom = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f

        // Calculate 35mm equivalent & zoom factor
        var equivalent35mm: Float? = null
        var zoomFactor = 1.0f

        if (sensorSize != null && sensorSize.width > 0 && sensorSize.height > 0) {
            val sensorDiagonal = Math.sqrt(
                (sensorSize.width * sensorSize.width + sensorSize.height * sensorSize.height).toDouble()
            ).toFloat()
            val fullFrameDiagonal = 43.27f // 35mm full frame diagonal
            val cropFactor = fullFrameDiagonal / sensorDiagonal
            equivalent35mm = primaryFocal * cropFactor
        }

        if (facing == LensFacingType.BACK) {
            zoomFactor = (primaryFocal / refBackFocalLength).coerceAtLeast(0.5f)
        }

        // Lens Category & Label
        val (lensCategory, shortLabel) = when (facing) {
            LensFacingType.FRONT -> "Front" to "Front"
            LensFacingType.EXTERNAL -> "External" to "Ext"
            LensFacingType.BACK -> {
                if (zoomFactor <= 0.75f || (equivalent35mm != null && equivalent35mm <= 20f)) {
                    "Ultrawide" to String.format("%.1f×", zoomFactor)
                } else if (zoomFactor >= 1.8f || (equivalent35mm != null && equivalent35mm >= 50f)) {
                    "Telephoto" to String.format("%.0f×", zoomFactor)
                } else {
                    "Main" to "1×"
                }
            }
        }

        val facingLabel = when (facing) {
            LensFacingType.FRONT -> "Front"
            LensFacingType.BACK -> "Rear"
            LensFacingType.EXTERNAL -> "External"
        }

        val displayName = if (facing == LensFacingType.FRONT) {
            "Front • 1×"
        } else {
            "$facingLabel • $shortLabel • $lensCategory"
        }

        return CameraInfoModel(
            cameraId = cameraId,
            facing = facing,
            displayName = displayName,
            shortLabel = shortLabel,
            lensCategory = lensCategory,
            focalLengths = focalLengths,
            sensorSize = sensorSize,
            equivalentFocalLength35mm = equivalent35mm,
            zoomFactor = zoomFactor,
            sensorOrientation = sensorOrientation,
            hasFlash = flashAvailable,
            supportsRaw = supportsRaw,
            isLogicalMultiCamera = isLogicalMulti,
            physicalCameraIds = physicalIds,
            hardwareLevel = hwLevel,
            hardwareLevelName = hwLevelName,
            supportedPhotoSizes = sortedPhotoSizes,
            maxPhotoSize = maxPhotoSize,
            supportedVideoSizes = sortedVideoSizes,
            supportedFpsRanges = fpsRanges,
            supportsVideoStabilization = supportsVideoStab,
            supportsOpticalStabilization = supportsOis,
            maxDigitalZoom = maxZoom
        )
    }
}
