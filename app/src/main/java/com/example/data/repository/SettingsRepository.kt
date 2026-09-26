package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val alsoSaveRaw: Boolean = true,
    val preferredVideoResolution: String = "1080p", // "4K", "1080p", "720p"
    val enableMicrophoneAudio: Boolean = true,
    val enableStabilization: Boolean = true,
    val lastSelectedCameraId: String? = null,
    val mirrorFrontCamera: Boolean = true
)

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("private_camera_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        return AppSettings(
            alsoSaveRaw = prefs.getBoolean("also_save_raw", true),
            preferredVideoResolution = prefs.getString("video_resolution", "1080p") ?: "1080p",
            enableMicrophoneAudio = prefs.getBoolean("mic_audio", true),
            enableStabilization = prefs.getBoolean("stabilization", true),
            lastSelectedCameraId = prefs.getString("last_camera_id", null),
            mirrorFrontCamera = prefs.getBoolean("mirror_front", true)
        )
    }

    fun setAlsoSaveRaw(enabled: Boolean) {
        prefs.edit().putBoolean("also_save_raw", enabled).apply()
        _settings.value = _settings.value.copy(alsoSaveRaw = enabled)
    }

    fun setPreferredVideoResolution(resolution: String) {
        prefs.edit().putString("video_resolution", resolution).apply()
        _settings.value = _settings.value.copy(preferredVideoResolution = resolution)
    }

    fun setEnableMicrophoneAudio(enabled: Boolean) {
        prefs.edit().putBoolean("mic_audio", enabled).apply()
        _settings.value = _settings.value.copy(enableMicrophoneAudio = enabled)
    }

    fun setEnableStabilization(enabled: Boolean) {
        prefs.edit().putBoolean("stabilization", enabled).apply()
        _settings.value = _settings.value.copy(enableStabilization = enabled)
    }

    fun setLastSelectedCameraId(cameraId: String) {
        prefs.edit().putString("last_camera_id", cameraId).apply()
        _settings.value = _settings.value.copy(lastSelectedCameraId = cameraId)
    }

    fun setMirrorFrontCamera(mirror: Boolean) {
        prefs.edit().putBoolean("mirror_front", mirror).apply()
        _settings.value = _settings.value.copy(mirrorFrontCamera = mirror)
    }
}
