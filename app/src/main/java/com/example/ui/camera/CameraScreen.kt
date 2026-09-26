package com.example.ui.camera

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.Surface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.camera.model.CameraInfoModel
import com.example.ui.theme.*
import com.example.ui.viewmodel.CameraMode
import com.example.ui.viewmodel.CameraViewModel

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    onNavigateToGallery: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val context = LocalContext.current

    val deviceOrientation = when (configuration.orientation) {
        Configuration.ORIENTATION_LANDSCAPE -> 90
        else -> 0
    }

    LaunchedEffect(Unit) {
        viewModel.refreshStorage()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepObsidian)
    ) {
        // 1. Camera Preview Surface (Aspect ratio preserved, hardware accelerated, zero stretching)
        if (uiState.isCameraPermissionGranted) {
            CameraPreviewView(
                sessionController = viewModel.sessionController,
                currentCamera = uiState.selectedCamera,
                currentZoomRatio = uiState.zoomRatio,
                onZoomChanged = { zoom -> viewModel.setZoom(zoom) },
                onTapToFocus = { normX, normY, w, h ->
                    viewModel.triggerTapToFocus(normX, normY, w, h)
                },
                activeFocusPoint = uiState.activeFocusPoint,
                exposureCompensation = uiState.exposureCompensation,
                onExposureChanged = { ev -> viewModel.setExposureCompensation(ev) },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Permission request prompt
            PermissionPlaceholder(onRequestPermissions = onRequestPermissions)
        }

        // 2. Active Recording Overlay Indicator (When Video is actively recording)
        if (uiState.isRecording) {
            ActiveRecordingBanner(
                durationMs = uiState.recordingDurationMs,
                cameraName = uiState.selectedCamera?.displayName ?: "Camera",
                isAudioEnabled = uiState.isAudioEnabled,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp)
            )
        }

        // 3. Top Controls Bar
        CameraTopBar(
            uiState = uiState,
            onFlashClick = { viewModel.cycleFlashMode() },
            onRawToggle = { viewModel.toggleRawSetting() },
            onResolutionSelect = { res -> viewModel.setVideoResolution(res) },
            onMicToggle = { viewModel.toggleAudio() },
            onInfoClick = { viewModel.openTechnicalDetailsModal() },
            onSettingsClick = onNavigateToSettings,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        )

        // 4. Capture Progress Spinner Overlay
        AnimatedVisibility(
            visible = uiState.isPhotoProcessing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = OverlayScrim,
                modifier = Modifier.padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        color = ElectricCyan,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "Encoding Lossless PNG...",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 5. Bottom Controls Bar (Pro EV/MF tuning, Lens selector, Mode switcher, Shutter button, Gallery button)
        CameraBottomControls(
            uiState = uiState,
            onModeSelect = { mode -> viewModel.setMode(mode) },
            onShutterClick = {
                if (uiState.cameraMode == CameraMode.PHOTO) {
                    viewModel.takePhoto(deviceOrientation)
                } else {
                    viewModel.toggleVideoRecording(deviceOrientation)
                }
            },
            onFlipCamera = {
                viewModel.switchFrontRear(null)
            },
            onSelectLens = { camera ->
                viewModel.selectCamera(camera, null)
            },
            onOpenAllCamerasSheet = {
                viewModel.openCameraSelectionSheet()
            },
            onGalleryClick = onNavigateToGallery,
            onToggleEv = { viewModel.toggleEvSlider() },
            onToggleFocus = { viewModel.toggleFocusSlider() },
            onEvChange = { ev -> viewModel.setExposureCompensation(ev) },
            onFocusDistanceChange = { dist ->
                viewModel.setFocusMode(isManual = true, distance = dist)
            },
            onResetEv = { viewModel.setExposureCompensation(0.0f) },
            onResetAf = { viewModel.resetToContinuousAf() },
            onCloseTuning = { viewModel.closeTuningSliders() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )

        // 6. Error Banner
        uiState.errorMessage?.let { errorMsg ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp, start = 16.dp, end = 16.dp),
                containerColor = RecordingRedDark,
                contentColor = TextPrimary,
                action = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("Dismiss", color = TextPrimary)
                    }
                }
            ) {
                Text(errorMsg)
            }
        }

        // 7. Camera Selection Bottom Sheet
        if (uiState.showCameraSelectionSheet) {
            CameraSelectionBottomSheet(
                cameras = uiState.discoveredCameras,
                selectedCamera = uiState.selectedCamera,
                onCameraSelect = { camera ->
                    viewModel.selectCamera(camera, null)
                },
                onDismiss = { viewModel.closeCameraSelectionSheet() }
            )
        }

        // 8. Technical Details Modal
        if (uiState.showTechnicalDetailsModal) {
            CameraTechnicalDetailsDialog(
                camera = uiState.selectedCamera,
                onDismiss = { viewModel.closeTechnicalDetailsModal() }
            )
        }
    }
}

@Composable
private fun ActiveRecordingBanner(
    durationMs: Long,
    cameraName: String,
    isAudioEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val totalSecs = durationMs / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    val timerText = String.format("%02d:%02d", mins, secs)

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = DeepObsidian.copy(alpha = 0.9f),
        border = androidx.compose.foundation.BorderStroke(1.dp, RecordingRed.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Glowing red recording dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(RecordingRed)
                )

                Text(
                    text = timerText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Text(
                    text = "•",
                    color = TextSecondary
                )

                Text(
                    text = if (isAudioEnabled) "Audio ON" else "MUTED",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isAudioEnabled) ElectricCyan else RecordingRed
                )
            }

            Text(
                text = "Recording continues if locked or backgrounded",
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun PermissionPlaceholder(onRequestPermissions: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                tint = CyanGaze,
                modifier = Modifier.size(64.dp)
            )

            Text(
                text = "Camera Access Required",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Text(
                text = "Private Camera needs direct access to your phone's camera sensors to discover lenses, take lossless photos, and record video.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Button(
                onClick = onRequestPermissions,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                modifier = Modifier.testTag("grant_permission_button")
            ) {
                Text("Grant Camera Permission", color = DeepObsidian, fontWeight = FontWeight.Bold)
            }
        }
    }
}
