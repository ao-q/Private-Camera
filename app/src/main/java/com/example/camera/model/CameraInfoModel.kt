package com.example.camera.model

import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import android.util.Range
import android.util.Size
import android.util.SizeF

enum class LensFacingType {
    BACK,
    FRONT,
    EXTERNAL
}

data class CameraInfoModel(
    val cameraId: String,
    val facing: LensFacingType,
    val displayName: String,
    val shortLabel: String, // e.g. "0.6x", "1x", "3x", "Front"
    val lensCategory: String, // "Ultrawide", "Main", "Telephoto", "Macro", "Front"
    val focalLengths: List<Float>,
    val sensorSize: SizeF?,
    val equivalentFocalLength35mm: Float?,
    val zoomFactor: Float,
    val sensorOrientation: Int,
    val hasFlash: Boolean,
    val supportsRaw: Boolean,
    val isLogicalMultiCamera: Boolean,
    val physicalCameraIds: Set<String>,
    val hardwareLevel: Int,
    val hardwareLevelName: String,
    val supportedPhotoSizes: List<Size>,
    val maxPhotoSize: Size,
    val supportedVideoSizes: List<Size>,
    val supportedFpsRanges: List<Range<Int>>,
    val supportsVideoStabilization: Boolean,
    val supportsOpticalStabilization: Boolean,
    val maxDigitalZoom: Float,
    val minFocusDistance: Float = 0.0f,
    val supportsManualFocus: Boolean = false,
    val aeCompensationRange: Range<Int> = Range(0, 0),
    val aeCompensationStep: Float = 0.333333f,
    val optimalPreviewSize: Size = Size(1440, 1080)
) {
    val isFrontCamera: Boolean get() = facing == LensFacingType.FRONT
    val isBackCamera: Boolean get() = facing == LensFacingType.BACK

    val minEv: Float get() = aeCompensationRange.lower * aeCompensationStep
    val maxEv: Float get() = aeCompensationRange.upper * aeCompensationStep
    val supportsExposureCompensation: Boolean get() = aeCompensationRange.lower != aeCompensationRange.upper
}
