package com.example.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Size
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.PrivateCameraApp
import com.example.camera.discovery.CameraDiscoveryManager
import com.example.camera.model.CameraInfoModel
import com.example.camera.model.LensFacingType
import com.example.camera.session.CameraSessionController
import com.example.camera.session.CaptureState
import com.example.camera.session.FlashMode
import com.example.data.local.MediaItemEntity
import com.example.data.repository.AppSettings
import com.example.data.repository.StorageInfo
import com.example.service.VideoRecordingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class CameraMode {
    PHOTO,
    VIDEO
}

data class CameraUiState(
    val discoveredCameras: List<CameraInfoModel> = emptyList(),
    val selectedCamera: CameraInfoModel? = null,
    val cameraMode: CameraMode = CameraMode.PHOTO,
    val flashMode: FlashMode = FlashMode.OFF,
    val zoomRatio: Float = 1.0f,
    val isRecording: Boolean = false,
    val recordingDurationMs: Long = 0L,
    val isPhotoProcessing: Boolean = false,
    val latestCapture: MediaItemEntity? = null,
    val storageInfo: StorageInfo? = null,
    val selectedVideoResolution: String = "1080p", // "4K", "1080p", "720p"
    val isAudioEnabled: Boolean = true,
    val alsoSaveRaw: Boolean = true,
    val errorMessage: String? = null,
    val isCameraPermissionGranted: Boolean = false,
    val isMicPermissionGranted: Boolean = false,
    val isNotificationPermissionGranted: Boolean = false,
    val showCameraSelectionSheet: Boolean = false,
    val showTechnicalDetailsModal: Boolean = false
)

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PrivateCameraApp
    private val discoveryManager = CameraDiscoveryManager(application)
    val sessionController = CameraSessionController(application, app.mediaRepository)

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private var recordingService: VideoRecordingService? = null
    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? VideoRecordingService.LocalBinder
            if (binder != null) {
                recordingService = binder.getService()
                isServiceBound = true
                sessionController.attachRecordingService(recordingService!!)

                viewModelScope.launch {
                    recordingService!!.isRecording.collect { rec ->
                        _uiState.update { it.copy(isRecording = rec) }
                    }
                }
                viewModelScope.launch {
                    recordingService!!.recordingDurationMs.collect { dur ->
                        _uiState.update { it.copy(recordingDurationMs = dur) }
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            recordingService = null
            isServiceBound = false
            sessionController.detachRecordingService()
        }
    }

    init {
        bindRecordingService()

        // Observe session controller state
        viewModelScope.launch {
            sessionController.captureState.collect { state ->
                _uiState.update { it.copy(isRecording = state == CaptureState.RECORDING_VIDEO) }
            }
        }
        viewModelScope.launch {
            sessionController.isPhotoProcessing.collect { processing ->
                _uiState.update { it.copy(isPhotoProcessing = processing) }
            }
        }
        viewModelScope.launch {
            sessionController.errorMessage.collect { err ->
                _uiState.update { it.copy(errorMessage = err) }
            }
        }
        viewModelScope.launch {
            sessionController.zoomRatio.collect { zoom ->
                _uiState.update { it.copy(zoomRatio = zoom) }
            }
        }

        // Observe latest media for gallery thumbnail
        viewModelScope.launch {
            app.mediaRepository.latestMedia.collect { latest ->
                _uiState.update { it.copy(latestCapture = latest) }
            }
        }

        // Observe settings
        viewModelScope.launch {
            app.settingsRepository.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        alsoSaveRaw = settings.alsoSaveRaw,
                        selectedVideoResolution = settings.preferredVideoResolution,
                        isAudioEnabled = settings.enableMicrophoneAudio
                    )
                }
            }
        }

        refreshStorage()
    }

    private fun bindRecordingService() {
        val intent = VideoRecordingService.startServiceIntent(getApplication())
        getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun onPermissionsUpdated(cameraGranted: Boolean, micGranted: Boolean, notifGranted: Boolean) {
        _uiState.update {
            it.copy(
                isCameraPermissionGranted = cameraGranted,
                isMicPermissionGranted = micGranted,
                isNotificationPermissionGranted = notifGranted
            )
        }
        if (cameraGranted) {
            discoverCamerasAndInitialize()
        }
    }

    fun discoverCamerasAndInitialize() {
        viewModelScope.launch {
            val cameras = discoveryManager.discoverAllCameras()
            if (cameras.isNotEmpty()) {
                val savedId = app.settingsRepository.settings.value.lastSelectedCameraId
                val defaultCamera = cameras.firstOrNull { it.cameraId == savedId }
                    ?: cameras.firstOrNull { it.facing == LensFacingType.BACK && it.zoomFactor in 0.9f..1.1f }
                    ?: cameras.first()

                _uiState.update {
                    it.copy(
                        discoveredCameras = cameras,
                        selectedCamera = defaultCamera
                    )
                }
            }
        }
    }

    fun selectCamera(camera: CameraInfoModel, previewSurface: Surface?) {
        _uiState.update { it.copy(selectedCamera = camera, showCameraSelectionSheet = false) }
        app.settingsRepository.setLastSelectedCameraId(camera.cameraId)
        sessionController.openCamera(camera, previewSurface)
    }

    fun switchFrontRear(previewSurface: Surface?) {
        val current = _uiState.value.selectedCamera ?: return
        val cameras = _uiState.value.discoveredCameras
        val targetFacing = if (current.isFrontCamera) LensFacingType.BACK else LensFacingType.FRONT
        val targetCamera = cameras.firstOrNull { it.facing == targetFacing } ?: return
        selectCamera(targetCamera, previewSurface)
    }

    fun setMode(mode: CameraMode) {
        _uiState.update { it.copy(cameraMode = mode) }
    }

    fun cycleFlashMode() {
        val current = _uiState.value.flashMode
        val next = when (current) {
            FlashMode.OFF -> FlashMode.AUTO
            FlashMode.AUTO -> FlashMode.ON
            FlashMode.ON -> FlashMode.TORCH
            FlashMode.TORCH -> FlashMode.OFF
        }
        _uiState.update { it.copy(flashMode = next) }
        sessionController.setFlashMode(next)
    }

    fun setZoom(ratio: Float) {
        sessionController.setZoom(ratio)
    }

    fun toggleAudio() {
        val current = _uiState.value.isAudioEnabled
        val next = !current
        _uiState.update { it.copy(isAudioEnabled = next) }
        app.settingsRepository.setEnableMicrophoneAudio(next)
    }

    fun setVideoResolution(res: String) {
        _uiState.update { it.copy(selectedVideoResolution = res) }
        app.settingsRepository.setPreferredVideoResolution(res)
    }

    fun toggleRawSetting() {
        val current = _uiState.value.alsoSaveRaw
        val next = !current
        _uiState.update { it.copy(alsoSaveRaw = next) }
        app.settingsRepository.setAlsoSaveRaw(next)
    }

    fun takePhoto(deviceOrientation: Int) {
        if (_uiState.value.isPhotoProcessing || _uiState.value.isRecording) return
        sessionController.captureLosslessPhoto(
            deviceOrientation = deviceOrientation,
            alsoSaveRaw = _uiState.value.alsoSaveRaw
        ) {
            refreshStorage()
        }
    }

    fun toggleVideoRecording(deviceOrientation: Int) {
        if (_uiState.value.isRecording) {
            sessionController.stopVideoRecording()
            refreshStorage()
        } else {
            val camera = _uiState.value.selectedCamera ?: return
            val res = _uiState.value.selectedVideoResolution
            val videoSize = when (res) {
                "4K" -> camera.supportedVideoSizes.firstOrNull { it.width >= 3840 || it.height >= 3840 }
                    ?: camera.supportedVideoSizes.firstOrNull { it.width >= 1920 } ?: Size(1920, 1080)
                "720p" -> camera.supportedVideoSizes.firstOrNull { it.width == 1280 || it.height == 720 }
                    ?: Size(1280, 720)
                else -> camera.supportedVideoSizes.firstOrNull { it.width == 1920 || it.height == 1080 }
                    ?: Size(1920, 1080)
            }

            sessionController.startVideoRecording(
                videoSize = videoSize,
                enableAudio = _uiState.value.isAudioEnabled && _uiState.value.isMicPermissionGranted,
                deviceOrientation = deviceOrientation
            )
        }
    }

    fun refreshStorage() {
        viewModelScope.launch {
            val info = app.storageMonitor.checkStorage()
            _uiState.update { it.copy(storageInfo = info) }
        }
    }

    fun openCameraSelectionSheet() {
        _uiState.update { it.copy(showCameraSelectionSheet = true) }
    }

    fun closeCameraSelectionSheet() {
        _uiState.update { it.copy(showCameraSelectionSheet = false) }
    }

    fun openTechnicalDetailsModal() {
        _uiState.update { it.copy(showTechnicalDetailsModal = true) }
    }

    fun closeTechnicalDetailsModal() {
        _uiState.update { it.copy(showTechnicalDetailsModal = false) }
    }

    fun clearError() {
        sessionController.clearError()
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        if (isServiceBound) {
            getApplication<Application>().unbindService(serviceConnection)
            isServiceBound = false
        }
        sessionController.release()
        super.onCleared()
    }
}
