package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.camera.CameraScreen
import com.example.ui.gallery.GalleryScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.DeepObsidian
import com.example.ui.theme.PrivateCameraTheme
import com.example.ui.viewer.MediaViewerScreen
import com.example.ui.viewmodel.CameraViewModel
import com.example.ui.viewmodel.GalleryViewModel
import com.example.ui.viewmodel.MediaViewerViewModel

sealed class AppScreen {
    data object Camera : AppScreen()
    data object Gallery : AppScreen()
    data class MediaViewer(val mediaId: String) : AppScreen()
    data object Settings : AppScreen()
}

class MainActivity : ComponentActivity() {

    private var cameraViewModelInstance: CameraViewModel? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] == true
        } else {
            true
        }

        cameraViewModelInstance?.onPermissionsUpdated(cameraGranted, micGranted, notifGranted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PrivateCameraTheme {
                val app = application as PrivateCameraApp
                val cameraViewModel: CameraViewModel = viewModel()
                val galleryViewModel: GalleryViewModel = viewModel()
                val mediaViewerViewModel: MediaViewerViewModel = viewModel()

                cameraViewModelInstance = cameraViewModel

                var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Camera) }

                LaunchedEffect(Unit) {
                    checkAndRequestPermissions()
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DeepObsidian
                ) {
                    AnimatedContent(
                        targetState = currentScreen,
                        label = "screen_transition"
                    ) { screen ->
                        when (screen) {
                            is AppScreen.Camera -> {
                                CameraScreen(
                                    viewModel = cameraViewModel,
                                    onNavigateToGallery = { currentScreen = AppScreen.Gallery },
                                    onNavigateToSettings = { currentScreen = AppScreen.Settings },
                                    onRequestPermissions = { requestAllPermissions() }
                                )
                            }
                            is AppScreen.Gallery -> {
                                GalleryScreen(
                                    viewModel = galleryViewModel,
                                    onNavigateBack = { currentScreen = AppScreen.Camera },
                                    onMediaClick = { id -> currentScreen = AppScreen.MediaViewer(id) }
                                )
                            }
                            is AppScreen.MediaViewer -> {
                                MediaViewerScreen(
                                    mediaId = screen.mediaId,
                                    viewModel = mediaViewerViewModel,
                                    onNavigateBack = { currentScreen = AppScreen.Gallery }
                                )
                            }
                            is AppScreen.Settings -> {
                                SettingsScreen(
                                    settingsRepository = app.settingsRepository,
                                    storageMonitor = app.storageMonitor,
                                    onNavigateBack = { currentScreen = AppScreen.Camera }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        cameraViewModelInstance?.onPermissionsUpdated(cameraGranted, micGranted, notifGranted)

        if (!cameraGranted || !micGranted || (!notifGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)) {
            requestAllPermissions()
        }
    }

    private fun requestAllPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    override fun onResume() {
        super.onResume()
        checkAndRequestPermissions()
    }
}
