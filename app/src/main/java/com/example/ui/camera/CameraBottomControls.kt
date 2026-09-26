package com.example.ui.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.camera.model.CameraInfoModel
import com.example.data.local.MediaType
import com.example.ui.theme.*
import com.example.ui.viewmodel.CameraMode
import com.example.ui.viewmodel.CameraUiState
import java.io.File

@Composable
fun CameraBottomControls(
    uiState: CameraUiState,
    onModeSelect: (CameraMode) -> Unit,
    onShutterClick: () -> Unit,
    onFlipCamera: () -> Unit,
    onSelectLens: (CameraInfoModel) -> Unit,
    onOpenAllCamerasSheet: () -> Unit,
    onGalleryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp, top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Lens Selector Bar (Quick pills for discovered lenses)
        if (uiState.discoveredCameras.isNotEmpty() && !uiState.isRecording) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(OverlayLightScrim)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                uiState.discoveredCameras.forEach { camera ->
                    val isSelected = uiState.selectedCamera?.cameraId == camera.cameraId
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) ElectricCyan else Color.Transparent)
                            .clickable { onSelectLens(camera) }
                            .testTag("lens_pill_${camera.cameraId}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = camera.shortLabel,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) DeepObsidian else TextPrimary
                        )
                    }
                }

                // Button to open full discovery sheet
                IconButton(
                    onClick = onOpenAllCamerasSheet,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .testTag("open_all_cameras_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "All Cameras & Physical Sensors",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 2. Mode Selector (Photo vs Video)
        if (!uiState.isRecording) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(OverlayLightScrim)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val isPhotoMode = uiState.cameraMode == CameraMode.PHOTO
                Surface(
                    onClick = { onModeSelect(CameraMode.PHOTO) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isPhotoMode) SurfaceElevated else Color.Transparent,
                    modifier = Modifier.testTag("photo_mode_tab")
                ) {
                    Text(
                        text = "PHOTO",
                        fontSize = 13.sp,
                        fontWeight = if (isPhotoMode) FontWeight.Bold else FontWeight.Normal,
                        color = if (isPhotoMode) ElectricCyan else TextSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                val isVideoMode = uiState.cameraMode == CameraMode.VIDEO
                Surface(
                    onClick = { onModeSelect(CameraMode.VIDEO) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isVideoMode) SurfaceElevated else Color.Transparent,
                    modifier = Modifier.testTag("video_mode_tab")
                ) {
                    Text(
                        text = "VIDEO",
                        fontSize = 13.sp,
                        fontWeight = if (isVideoMode) FontWeight.Bold else FontWeight.Normal,
                        color = if (isVideoMode) RecordingRed else TextSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // 3. Shutter Row (Gallery Thumbnail | Shutter Button | Front/Rear Flip)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gallery Thumbnail / Shortcut
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(OverlayLightScrim)
                    .border(1.5.dp, SurfaceElevated, RoundedCornerShape(14.dp))
                    .clickable(enabled = !uiState.isRecording) { onGalleryClick() }
                    .testTag("gallery_thumbnail_button"),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.latestCapture != null && File(uiState.latestCapture.thumbnailPath).exists()) {
                    AsyncImage(
                        model = File(uiState.latestCapture.thumbnailPath),
                        contentDescription = "Latest capture gallery thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Small badge overlay
                    val badge = when {
                        uiState.latestCapture.mediaType == MediaType.VIDEO -> "VID"
                        uiState.latestCapture.hasRaw -> "RAW"
                        else -> "PNG"
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(2.dp)
                            .background(DeepObsidian.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 3.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = badge,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (badge == "RAW") RawPurple else if (badge == "VID") RecordingRed else ElectricCyan
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Private In-App Gallery",
                        tint = TextSecondary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Central Shutter Button
            ShutterButton(
                uiState = uiState,
                onClick = onShutterClick
            )

            // Front/Rear Camera Flip Button
            IconButton(
                onClick = onFlipCamera,
                enabled = !uiState.isRecording,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(OverlayLightScrim)
                    .border(1.dp, SurfaceElevated, CircleShape)
                    .testTag("camera_flip_button")
            ) {
                Icon(
                    imageVector = Icons.Default.FlipCameraAndroid,
                    contentDescription = "Switch front and rear camera",
                    tint = TextPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun ShutterButton(
    uiState: CameraUiState,
    onClick: () -> Unit
) {
    val isRecording = uiState.isRecording
    val isPhotoMode = uiState.cameraMode == CameraMode.PHOTO

    // Recording pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "recording_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .clickable(enabled = !uiState.isPhotoProcessing) { onClick() }
            .testTag("shutter_record_button"),
        contentAlignment = Alignment.Center
    ) {
        // Outer Ring
        Box(
            modifier = Modifier
                .size(80.dp)
                .border(
                    width = 4.dp,
                    color = if (isRecording) RecordingRed else ShutterRing,
                    shape = CircleShape
                )
        )

        // Inner Action Element
        if (uiState.isPhotoProcessing) {
            CircularProgressIndicator(
                color = ElectricCyan,
                strokeWidth = 3.dp,
                modifier = Modifier.size(60.dp)
            )
        } else if (isPhotoMode) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        } else {
            // Video Mode
            if (isRecording) {
                // Square Stop Box inside pulsing ring
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(RecordingRed)
                )
            } else {
                // Large Red circle
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(RecordingRed)
                )
            }
        }
    }
}
