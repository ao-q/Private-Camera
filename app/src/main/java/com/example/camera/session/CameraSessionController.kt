package com.example.camera.session

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import com.example.camera.capture.LosslessPngEncoder
import com.example.camera.capture.RawDngWriter
import com.example.camera.model.CameraInfoModel
import com.example.camera.model.LensFacingType
import com.example.data.local.MediaItemEntity
import com.example.data.repository.MediaRepository
import com.example.service.VideoRecordingService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class FlashMode {
    OFF,
    AUTO,
    ON,
    TORCH
}

enum class CaptureState {
    IDLE,
    CAPTURING_PHOTO,
    RECORDING_VIDEO,
    ERROR
}

class CameraSessionController(
    private val context: Context,
    private val mediaRepository: MediaRepository
) {
    private val tag = "CameraSessionController"
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null

    private var previewSurface: Surface? = null
    private var yuvImageReader: ImageReader? = null
    private var rawImageReader: ImageReader? = null

    private var mediaRecorder: MediaRecorder? = null
    private var recorderSurface: Surface? = null
    private var currentVideoTempFile: File? = null

    private val controllerScope = CoroutineScope(Dispatchers.Main + Job())

    // Active state
    private val _captureState = MutableStateFlow(CaptureState.IDLE)
    val captureState: StateFlow<CaptureState> = _captureState.asStateFlow()

    private val _currentCamera = MutableStateFlow<CameraInfoModel?>(null)
    val currentCamera: StateFlow<CameraInfoModel?> = _currentCamera.asStateFlow()

    private val _flashMode = MutableStateFlow(FlashMode.OFF)
    val flashMode: StateFlow<FlashMode> = _flashMode.asStateFlow()

    private val _zoomRatio = MutableStateFlow(1.0f)
    val zoomRatio: StateFlow<Float> = _zoomRatio.asStateFlow()

    private val _exposureCompensation = MutableStateFlow(0.0f) // in EV (e.g. -2.0 to +2.0)
    val exposureCompensation: StateFlow<Float> = _exposureCompensation.asStateFlow()

    private val _isManualFocus = MutableStateFlow(false)
    val isManualFocus: StateFlow<Boolean> = _isManualFocus.asStateFlow()

    private val _manualFocusDistance = MutableStateFlow(0.0f) // in diopters (0 = infinity)
    val manualFocusDistance: StateFlow<Float> = _manualFocusDistance.asStateFlow()

    private val _activeFocusPoint = MutableStateFlow<Pair<Float, Float>?>(null)
    val activeFocusPoint: StateFlow<Pair<Float, Float>?> = _activeFocusPoint.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isPhotoProcessing = MutableStateFlow(false)
    val isPhotoProcessing: StateFlow<Boolean> = _isPhotoProcessing.asStateFlow()

    private var currentCharacteristics: CameraCharacteristics? = null
    private var lastCaptureResult: CaptureResult? = null
    private var activeAfMeteringRect: android.hardware.camera2.params.MeteringRectangle? = null

    // Service reference for foreground background recording
    private var recordingService: VideoRecordingService? = null

    init {
        startBackgroundThread()
    }

    fun attachRecordingService(service: VideoRecordingService) {
        recordingService = service
        service.onRecordingCompleted = { savedMedia ->
            _captureState.value = CaptureState.IDLE
        }
    }

    fun detachRecordingService() {
        recordingService = null
    }

    private fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("PrivateCameraBackground").apply {
                start()
                backgroundHandler = Handler(looper)
            }
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setFlashMode(mode: FlashMode) {
        _flashMode.value = mode
        applyPreviewSettings()
    }

    fun setZoom(ratio: Float) {
        val camera = _currentCamera.value ?: return
        val clamped = ratio.coerceIn(1.0f, camera.maxDigitalZoom.coerceAtLeast(1.0f))
        _zoomRatio.value = clamped
        applyPreviewSettings()
    }

    fun setExposureCompensation(ev: Float) {
        val camera = _currentCamera.value ?: return
        val clamped = if (camera.supportsExposureCompensation) {
            ev.coerceIn(camera.minEv, camera.maxEv)
        } else 0.0f
        _exposureCompensation.value = clamped
        applyPreviewSettings()
    }

    fun setFocusMode(isManual: Boolean, distance: Float = 0.0f) {
        _isManualFocus.value = isManual
        val camera = _currentCamera.value
        val maxDist = camera?.minFocusDistance ?: 10.0f
        _manualFocusDistance.value = distance.coerceIn(0.0f, maxDist)
        if (!isManual) {
            activeAfMeteringRect = null
            _activeFocusPoint.value = null
        }
        applyPreviewSettings()
    }

    fun setManualFocusDistance(distance: Float) {
        val camera = _currentCamera.value
        val maxDist = camera?.minFocusDistance ?: 10.0f
        _manualFocusDistance.value = distance.coerceIn(0.0f, maxDist)
        applyPreviewSettings()
    }

    fun triggerTapToFocus(normX: Float, normY: Float, viewWidth: Float, viewHeight: Float) {
        val camera = _currentCamera.value ?: return
        val chars = currentCharacteristics ?: return
        val activeArray = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return
        val session = captureSession ?: return
        val builder = previewRequestBuilder ?: return

        _isManualFocus.value = false
        _activeFocusPoint.value = Pair(normX * viewWidth, normY * viewHeight)

        // Calculate sensor coordinates with correct rotation & mirroring
        val sensorOrientation = camera.sensorOrientation
        val isFront = camera.isFrontCamera

        val (sensorNormX, sensorNormY) = when (sensorOrientation) {
            90 -> Pair(normY, 1.0f - normX)
            270 -> if (isFront) Pair(1.0f - normY, 1.0f - normX) else Pair(1.0f - normY, normX)
            180 -> Pair(1.0f - normX, 1.0f - normY)
            else -> Pair(normX, normY)
        }

        val ratio = _zoomRatio.value
        val cropW = (activeArray.width() / ratio).toInt()
        val cropH = (activeArray.height() / ratio).toInt()
        val cropX = (activeArray.width() - cropW) / 2
        val cropY = (activeArray.height() - cropH) / 2

        val centerX = cropX + (sensorNormX.coerceIn(0f, 1f) * cropW).toInt()
        val centerY = cropY + (sensorNormY.coerceIn(0f, 1f) * cropH).toInt()
        val halfSize = (cropW * 0.08f).toInt().coerceAtLeast(60)

        val meteringRect = Rect(
            (centerX - halfSize).coerceIn(activeArray.left, activeArray.right),
            (centerY - halfSize).coerceIn(activeArray.top, activeArray.bottom),
            (centerX + halfSize).coerceIn(activeArray.left, activeArray.right),
            (centerY + halfSize).coerceIn(activeArray.top, activeArray.bottom)
        )
        val metering = android.hardware.camera2.params.MeteringRectangle(
            meteringRect,
            android.hardware.camera2.params.MeteringRectangle.METERING_WEIGHT_MAX
        )
        activeAfMeteringRect = metering

        try {
            // Cancel any in-flight trigger
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL)
            builder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL)
            session.capture(builder.build(), null, backgroundHandler)

            // Trigger AF and AE precapture metering
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
            builder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(metering))
            builder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(metering))
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
            builder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START)
            session.capture(builder.build(), null, backgroundHandler)

            // Restore idle trigger for the continuous repeating preview stream
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
            builder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE)
            session.setRepeatingRequest(builder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    lastCaptureResult = result
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e(tag, "Failed to trigger tap-to-focus", e)
        }
    }

    fun resetToContinuousAf() {
        _activeFocusPoint.value = null
        activeAfMeteringRect = null
        _isManualFocus.value = false
        applyPreviewSettings()
    }

    @SuppressLint("MissingPermission")
    fun openCamera(cameraModel: CameraInfoModel, newPreviewSurface: Surface?) {
        if (_captureState.value == CaptureState.RECORDING_VIDEO) {
            // If already recording, attach preview surface safely without disrupting the recording
            if (newPreviewSurface != null) {
                attachPreviewSurfaceDuringRecording(newPreviewSurface)
            }
            return
        }

        closeCamera()
        _currentCamera.value = cameraModel
        _zoomRatio.value = 1.0f
        previewSurface = newPreviewSurface

        try {
            currentCharacteristics = cameraManager.getCameraCharacteristics(cameraModel.cameraId)
            setupImageReaders(cameraModel)

            cameraManager.openCamera(
                cameraModel.cameraId,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cameraDevice = camera
                        createPreviewSession()
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        cameraDevice = null
                        _errorMessage.value = "Camera ${cameraModel.shortLabel} was disconnected"
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        camera.close()
                        cameraDevice = null
                        val errText = when (error) {
                            ERROR_CAMERA_IN_USE -> "Camera is currently in use by another app"
                            ERROR_MAX_CAMERAS_IN_USE -> "Maximum camera limit reached"
                            ERROR_CAMERA_DISABLED -> "Camera is disabled by device policy"
                            ERROR_CAMERA_DEVICE -> "Camera device encountered a fatal error"
                            ERROR_CAMERA_SERVICE -> "Camera system service error"
                            else -> "Camera error ($error)"
                        }
                        _errorMessage.value = errText
                        _captureState.value = CaptureState.ERROR
                    }
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            Log.e(tag, "Error opening camera ${cameraModel.cameraId}", e)
            _errorMessage.value = "Failed to open camera: ${e.message}"
        }
    }

    private fun setupImageReaders(cameraModel: CameraInfoModel) {
        val photoSize = cameraModel.maxPhotoSize
        yuvImageReader?.close()
        yuvImageReader = ImageReader.newInstance(
            photoSize.width,
            photoSize.height,
            ImageFormat.YUV_420_888,
            2
        )

        rawImageReader?.close()
        if (cameraModel.supportsRaw) {
            val map = currentCharacteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val rawSizes = map?.getOutputSizes(ImageFormat.RAW_SENSOR)
            val maxRawSize = rawSizes?.maxByOrNull { it.width * it.height }
            if (maxRawSize != null) {
                rawImageReader = ImageReader.newInstance(
                    maxRawSize.width,
                    maxRawSize.height,
                    ImageFormat.RAW_SENSOR,
                    2
                )
            }
        } else {
            rawImageReader = null
        }
    }

    private fun createPreviewSession() {
        val device = cameraDevice ?: return
        val handler = backgroundHandler ?: return

        try {
            val surfaces = mutableListOf<Surface>()
            previewSurface?.let { surfaces.add(it) }
            yuvImageReader?.surface?.let { surfaces.add(it) }
            rawImageReader?.surface?.let { surfaces.add(it) }

            if (surfaces.isEmpty()) return

            device.createCaptureSession(
                surfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cameraDevice == null) return
                        captureSession = session
                        try {
                            previewRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                previewSurface?.let { addTarget(it) }
                                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                            }
                            applyPreviewSettings()
                        } catch (e: Exception) {
                            Log.e(tag, "Failed to start preview repeating request", e)
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        _errorMessage.value = "Failed to configure camera preview session"
                    }
                },
                handler
            )
        } catch (e: Exception) {
            Log.e(tag, "Error creating preview session", e)
        }
    }

    private fun applyPreviewSettings() {
        val builder = previewRequestBuilder ?: return
        val session = captureSession ?: return
        val chars = currentCharacteristics ?: return
        val camera = _currentCamera.value

        // Flash control
        when (_flashMode.value) {
            FlashMode.OFF -> {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            }
            FlashMode.AUTO -> {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            }
            FlashMode.ON -> {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE)
            }
            FlashMode.TORCH -> {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
            }
        }

        // Exposure compensation (EV tuning)
        if (camera != null && camera.supportsExposureCompensation) {
            val range = camera.aeCompensationRange
            val step = camera.aeCompensationStep
            val ev = _exposureCompensation.value
            val index = Math.round(ev / step).coerceIn(range.lower, range.upper)
            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, index)
        }

        // Focus mode: Manual vs Auto
        if (_isManualFocus.value) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, _manualFocusDistance.value)
        } else {
            val tapRegion = activeAfMeteringRect
            if (tapRegion != null) {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                builder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(tapRegion))
                builder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(tapRegion))
            } else {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }
        }

        // Digital Zoom / Crop region
        val activeArray = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        if (activeArray != null) {
            val ratio = _zoomRatio.value
            val cropW = (activeArray.width() / ratio).toInt()
            val cropH = (activeArray.height() / ratio).toInt()
            val cropX = (activeArray.width() - cropW) / 2
            val cropY = (activeArray.height() - cropH) / 2
            val cropRegion = Rect(cropX, cropY, cropX + cropW, cropY + cropH)
            builder.set(CaptureRequest.SCALER_CROP_REGION, cropRegion)
        }

        // Preview stream latency & frame rate optimization (Lag-free preview)
        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_FAST)
        builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_FAST)
        builder.set(CaptureRequest.HOT_PIXEL_MODE, CaptureRequest.HOT_PIXEL_MODE_FAST)

        // Best 30/60 fps range to eliminate preview lag and stutter
        val fpsRanges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
        val bestFpsRange = fpsRanges?.firstOrNull { it.lower >= 30 && it.upper >= 30 }
            ?: fpsRanges?.firstOrNull { it.upper >= 30 }
            ?: fpsRanges?.firstOrNull()
        if (bestFpsRange != null) {
            builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, bestFpsRange)
        }

        try {
            session.setRepeatingRequest(
                builder.build(),
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        lastCaptureResult = result
                    }
                },
                backgroundHandler
            )
        } catch (_: Exception) {}
    }

    fun applyFlashAndZoom() {
        applyPreviewSettings()
    }

    fun onPreviewSurfaceAvailable(surface: Surface) {
        previewSurface = surface
        val camera = _currentCamera.value
        if (camera != null) {
            if (_captureState.value == CaptureState.RECORDING_VIDEO) {
                attachPreviewSurfaceDuringRecording(surface)
            } else if (cameraDevice != null) {
                createPreviewSession()
            } else {
                openCamera(camera, surface)
            }
        }
    }

    fun onPreviewSurfaceDestroyed() {
        previewSurface = null
        if (_captureState.value == CaptureState.RECORDING_VIDEO) {
            // Screen is turning off or activity backgrounded during active video recording!
            // Do NOT stop cameraDevice or MediaRecorder; keep recording alive!
            detachPreviewSurfaceDuringRecording()
        }
    }

    /**
     * Lossless PNG photo capture (+ optional RAW DNG)
     */
    fun captureLosslessPhoto(
        deviceOrientation: Int,
        alsoSaveRaw: Boolean,
        onCaptureCompleted: (MediaItemEntity?) -> Unit
    ) {
        val device = cameraDevice ?: return
        val session = captureSession ?: return
        val camera = _currentCamera.value ?: return
        val yuvReader = yuvImageReader ?: return

        if (_isPhotoProcessing.value) return
        _isPhotoProcessing.value = true
        _captureState.value = CaptureState.CAPTURING_PHOTO

        val tempPngFile = mediaRepository.createTempFile("photo", ".png")
        val tempDngFile = if (alsoSaveRaw && camera.supportsRaw && rawImageReader != null) {
            mediaRepository.createTempFile("raw", ".dng")
        } else null

        val captureBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
            addTarget(yuvReader.surface)
            tempDngFile?.let {
                rawImageReader?.surface?.let { rawSurf -> addTarget(rawSurf) }
            }
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)

            // Calculate rotation for correct image orientation
            val sensorOrientation = camera.sensorOrientation
            val isFront = camera.facing == LensFacingType.FRONT
            val rotation = if (isFront) {
                (sensorOrientation + deviceOrientation) % 360
            } else {
                (sensorOrientation - deviceOrientation + 360) % 360
            }
            set(CaptureRequest.JPEG_ORIENTATION, rotation)
        }

        var yuvProcessed = false
        var rawProcessed = tempDngFile == null

        var capturedYuvImage: Image? = null
        var capturedRawImage: Image? = null
        var totalResult: TotalCaptureResult? = null

        fun checkAndFinalize() {
            if (yuvProcessed && rawProcessed) {
                controllerScope.launch(Dispatchers.IO) {
                    val result = totalResult
                    val iso = result?.get(CaptureResult.SENSOR_SENSITIVITY)
                    val expTime = result?.get(CaptureResult.SENSOR_EXPOSURE_TIME)?.let { nanos ->
                        if (nanos > 0) {
                            val seconds = nanos / 1_000_000_000.0
                            if (seconds < 1.0) "1/${(1.0 / seconds).toInt()}s" else String.format("%.1fs", seconds)
                        } else null
                    }
                    val focal = result?.get(CaptureResult.LENS_FOCAL_LENGTH)
                    val aperture = result?.get(CaptureResult.LENS_APERTURE)

                    val entity = mediaRepository.finalizePhotoCapture(
                        tempPngFile = tempPngFile,
                        tempDngFile = tempDngFile,
                        cameraId = camera.cameraId,
                        cameraLabel = camera.displayName,
                        width = camera.maxPhotoSize.width,
                        height = camera.maxPhotoSize.height,
                        orientationDegrees = camera.sensorOrientation,
                        iso = iso,
                        exposureTime = expTime,
                        focalLength = focal,
                        aperture = aperture
                    )

                    withContext(Dispatchers.Main) {
                        _isPhotoProcessing.value = false
                        _captureState.value = CaptureState.IDLE
                        onCaptureCompleted(entity)
                    }
                }
            }
        }

        yuvReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                controllerScope.launch(Dispatchers.Default) {
                    val mirror = camera.facing == LensFacingType.FRONT
                    LosslessPngEncoder.encodeYuvToPng(
                        image = image,
                        destinationFile = tempPngFile,
                        rotationDegrees = camera.sensorOrientation,
                        mirrorHorizontal = mirror
                    )
                    image.close()
                    yuvProcessed = true
                    checkAndFinalize()
                }
            }
        }, backgroundHandler)

        if (tempDngFile != null && rawImageReader != null) {
            rawImageReader!!.setOnImageAvailableListener({ reader ->
                val rawImage = reader.acquireLatestImage()
                if (rawImage != null) {
                    controllerScope.launch(Dispatchers.IO) {
                        val chars = currentCharacteristics
                        val result = totalResult ?: lastCaptureResult
                        if (chars != null && result != null) {
                            RawDngWriter.writeRawDng(
                                image = rawImage,
                                characteristics = chars,
                                captureResult = result,
                                destinationFile = tempDngFile,
                                orientation = camera.sensorOrientation
                            )
                        }
                        rawImage!!.close()
                        rawProcessed = true
                        checkAndFinalize()
                    }
                }
            }, backgroundHandler)
        }

        try {
            session.capture(
                captureBuilder.build(),
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        totalResult = result
                        lastCaptureResult = result
                    }

                    override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                        _isPhotoProcessing.value = false
                        _captureState.value = CaptureState.IDLE
                        _errorMessage.value = "Photo capture failed: ${failure.reason}"
                        onCaptureCompleted(null)
                    }
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to send still capture request", e)
            _isPhotoProcessing.value = false
            _captureState.value = CaptureState.IDLE
            onCaptureCompleted(null)
        }
    }

    /**
     * Start continuous video recording (at high resolution with optional mic audio)
     */
    fun startVideoRecording(
        videoSize: Size,
        enableAudio: Boolean,
        deviceOrientation: Int
    ) {
        val device = cameraDevice ?: return
        val camera = _currentCamera.value ?: return

        try {
            closePreviewSession()

            val tempFile = mediaRepository.createTempFile("video", ".mp4")
            currentVideoTempFile = tempFile

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder = recorder

            if (enableAudio) {
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            }
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setOutputFile(tempFile.absolutePath)

            // High bitrate for top video quality
            val is4K = videoSize.width >= 3840 || videoSize.height >= 3840
            val videoBitRate = if (is4K) 45_000_000 else 18_000_000
            recorder.setVideoEncodingBitRate(videoBitRate)
            recorder.setVideoFrameRate(30)
            recorder.setVideoSize(videoSize.width, videoSize.height)
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)

            if (enableAudio) {
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioEncodingBitRate(192_000)
                recorder.setAudioSamplingRate(48_000)
            }

            // Orientation hint
            val sensorOrientation = camera.sensorOrientation
            val isFront = camera.facing == LensFacingType.FRONT
            val rotationHint = if (isFront) {
                (sensorOrientation + deviceOrientation) % 360
            } else {
                (sensorOrientation - deviceOrientation + 360) % 360
            }
            recorder.setOrientationHint(rotationHint)
            recorder.prepare()

            recorderSurface = recorder.surface

            val surfaces = mutableListOf<Surface>()
            previewSurface?.let { surfaces.add(it) }
            recorderSurface?.let { surfaces.add(it) }

            device.createCaptureSession(
                surfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            val recordBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                                previewSurface?.let { addTarget(it) }
                                recorderSurface?.let { addTarget(it) }
                                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)

                                if (camera.supportsVideoStabilization) {
                                    set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)
                                }
                            }
                            previewRequestBuilder = recordBuilder
                            session.setRepeatingRequest(recordBuilder.build(), null, backgroundHandler)

                            recorder.start()
                            _captureState.value = CaptureState.RECORDING_VIDEO

                            // Activate foreground service
                            recordingService?.startForegroundRecording(
                                cameraModel = camera,
                                audioEnabled = enableAudio,
                                tempVideoFile = tempFile,
                                width = videoSize.width,
                                height = videoSize.height,
                                orientation = rotationHint
                            )
                        } catch (e: Exception) {
                            Log.e(tag, "Error starting MediaRecorder or recording session", e)
                            _errorMessage.value = "Failed to start recording: ${e.message}"
                            _captureState.value = CaptureState.ERROR
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        _errorMessage.value = "Failed to configure video recording session"
                        _captureState.value = CaptureState.ERROR
                    }
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            Log.e(tag, "Exception during video recording setup", e)
            _errorMessage.value = "Recording error: ${e.message}"
            _captureState.value = CaptureState.ERROR
        }
    }

    fun stopVideoRecording(): MediaItemEntity? {
        if (_captureState.value != CaptureState.RECORDING_VIDEO) return null
        _captureState.value = CaptureState.IDLE

        try {
            captureSession?.stopRepeating()
            captureSession?.abortCaptures()
        } catch (_: Exception) {}

        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            mediaRecorder?.release()
            mediaRecorder = null
        } catch (e: Exception) {
            Log.e(tag, "Error stopping MediaRecorder", e)
        }

        recorderSurface = null

        val savedEntity = recordingService?.stopActiveRecording()

        // Rebuild clean preview session
        val camera = _currentCamera.value
        if (camera != null && cameraDevice != null) {
            createPreviewSession()
        }

        return savedEntity
    }

    private fun attachPreviewSurfaceDuringRecording(surface: Surface) {
        val device = cameraDevice ?: return
        val recSurf = recorderSurface ?: return
        previewSurface = surface

        try {
            val surfaces = listOf(recSurf, surface)
            device.createCaptureSession(
                surfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                                addTarget(surface)
                                addTarget(recSurf)
                                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                            }
                            previewRequestBuilder = builder
                            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
                        } catch (e: Exception) {
                            Log.e(tag, "Error updating repeating request with new preview surface", e)
                        }
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {}
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to re-attach preview surface during recording", e)
        }
    }

    private fun detachPreviewSurfaceDuringRecording() {
        val device = cameraDevice ?: return
        val recSurf = recorderSurface ?: return

        try {
            val surfaces = listOf(recSurf)
            device.createCaptureSession(
                surfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                                addTarget(recSurf)
                                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                            }
                            previewRequestBuilder = builder
                            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
                        } catch (_: Exception) {}
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {}
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to detach preview surface during background recording", e)
        }
    }

    private fun closePreviewSession() {
        try {
            captureSession?.close()
            captureSession = null
        } catch (_: Exception) {}
    }

    fun closeCamera() {
        closePreviewSession()
        try {
            cameraDevice?.close()
            cameraDevice = null
        } catch (_: Exception) {}
        try {
            yuvImageReader?.close()
            yuvImageReader = null
            rawImageReader?.close()
            rawImageReader = null
        } catch (_: Exception) {}
    }

    fun release() {
        closeCamera()
        stopBackgroundThread()
        controllerScope.cancel()
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
