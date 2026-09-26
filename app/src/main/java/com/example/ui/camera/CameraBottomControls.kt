package com.example.ui.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
    onToggleEv: () -> Unit = {},
    onToggleFocus: () -> Unit = {},
    onEvChange: (Float) -> Unit = {},
    onFocusDistanceChange: (Float) -> Unit = {},
    onResetEv: () -> Unit = {},
    onResetAf: () -> Unit = {},
    onCloseTuning: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp, top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val selectedCam = uiState.selectedCamera

        // 0. Pro Focus & Exposure Tuning Drawer (Expands smoothly when EV or Focus is selected)
        AnimatedVisibility(
            visible = (uiState.showEvSlider || uiState.showFocusSlider) && !uiState.isRecording,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SurfaceElevated.copy(alpha = 0.95f),
                border = androidx.compose.foundation.BorderStroke(1.dp, ElectricGreen.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.showEvSlider) {
                        // --- EV EXPOSURE TUNING SLIDER ---
                        val minEv = selectedCam?.minEv ?: -2.0f
                        val maxEv = selectedCam?.maxEv ?: 2.0f

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WbSunny,
                                    contentDescription = "Exposure",
                                    tint = ElectricGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "EXPOSURE COMPENSATION",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary,
                                    letterSpacing = 1.sp
                                )
                            }
                            Text(
                                text = if (uiState.exposureCompensation >= 0f) String.format("+%.1f EV", uiState.exposureCompensation) else String.format("%.1f EV", uiState.exposureCompensation),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricGreen
                            )
                        }

                        if (selectedCam?.supportsExposureCompensation == true) {
                            Slider(
                                value = uiState.exposureCompensation,
                                onValueChange = { onEvChange(it) },
                                valueRange = minEv..maxEv,
                                colors = SliderDefaults.colors(
                                    thumbColor = ElectricGreen,
                                    activeTrackColor = ElectricGreen,
                                    inactiveTrackColor = SurfaceDark
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = "Exposure compensation not supported on this sensor",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = onResetEv,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Reset (0.0 EV)", fontSize = 12.sp, color = TextPrimary)
                            }
                            IconButton(
                                onClick = onCloseTuning,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Done", tint = ElectricGreen, modifier = Modifier.size(18.dp))
                            }
                        }
                    } else if (uiState.showFocusSlider) {
                        // --- MANUAL FOCUS DISTANCE SLIDER ---
                        val maxDist = selectedCam?.minFocusDistance ?: 10.0f
                        val supportsMf = selectedCam?.supportsManualFocus == true

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterCenterFocus,
                                    contentDescription = "Focus",
                                    tint = ElectricGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (uiState.isManualFocus) "MANUAL FOCUS (MF)" else "AUTO FOCUS (AF)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary,
                                    letterSpacing = 1.sp
                                )
                            }
                            Text(
                                text = if (!uiState.isManualFocus) {
                                    "AF Continuous"
                                } else if (uiState.manualFocusDistance <= 0.05f) {
                                    "∞ Infinity"
                                } else if (uiState.manualFocusDistance >= maxDist * 0.9f) {
                                    "Macro"
                                } else {
                                    String.format("%.1fm", 1.0f / uiState.manualFocusDistance.coerceAtLeast(0.1f))
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricGreen
                            )
                        }

                        if (supportsMf) {
                            Slider(
                                value = uiState.manualFocusDistance,
                                onValueChange = { onFocusDistanceChange(it) },
                                valueRange = 0.0f..maxDist,
                                colors = SliderDefaults.colors(
                                    thumbColor = ElectricGreen,
                                    activeTrackColor = ElectricGreen,
                                    inactiveTrackColor = SurfaceDark
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("∞ (Infinity)", fontSize = 11.sp, color = TextTertiary)
                                Text("1.0m", fontSize = 11.sp, color = TextTertiary)
                                Text("Macro", fontSize = 11.sp, color = TextTertiary)
                            }
                        } else {
                            Text(
                                text = "Fixed focus camera (Manual focus distance not supported by hardware)",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = onResetAf,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Switch to Auto Focus (AF)", fontSize = 12.sp, color = TextPrimary)
                            }
                            IconButton(
                                onClick = onCloseTuning,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Done", tint = ElectricGreen, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        // 1. Pro Quick Tuning Bar (EV and AF/MF buttons right above the lens pills)
        if (!uiState.isRecording) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(OverlayLightScrim)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // EV Tuning Pill Button
                val evActive = uiState.showEvSlider || uiState.exposureCompensation != 0.0f
                Surface(
                    onClick = onToggleEv,
                    shape = RoundedCornerShape(14.dp),
                    color = if (evActive) ElectricGreen.copy(alpha = 0.2f) else Color.Transparent,
                    border = if (evActive) androidx.compose.foundation.BorderStroke(1.dp, ElectricGreen) else null,
                    modifier = Modifier.testTag("ev_tuning_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WbSunny,
                            contentDescription = "EV",
                            tint = if (evActive) ElectricGreen else TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (uiState.exposureCompensation == 0f) "EV ±0.0" else if (uiState.exposureCompensation > 0) String.format("+%.1f", uiState.exposureCompensation) else String.format("%.1f", uiState.exposureCompensation),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (evActive) ElectricGreen else TextPrimary
                        )
                    }
                }

                // AF / MF Mode Pill Button
                val mfActive = uiState.showFocusSlider || uiState.isManualFocus
                Surface(
                    onClick = onToggleFocus,
                    shape = RoundedCornerShape(14.dp),
                    color = if (mfActive) ElectricGreen.copy(alpha = 0.2f) else Color.Transparent,
                    border = if (mfActive) androidx.compose.foundation.BorderStroke(1.dp, ElectricGreen) else null,
                    modifier = Modifier.testTag("focus_tuning_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterCenterFocus,
                            contentDescription = "Focus Mode",
                            tint = if (mfActive) ElectricGreen else TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (uiState.isManualFocus) "MF" else "AF",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (mfActive) ElectricGreen else TextPrimary
                        )
                    }
                }
            }
        }

        // 2. Lens Selector Bar (Quick pills for discovered physical/logical lenses)
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
                            .background(if (isSelected) ElectricGreen else Color.Transparent)
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

        // 3. Mode Selector (Photo vs Video)
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
                        color = if (isPhotoMode) ElectricGreen else TextSecondary,
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

        // 4. Shutter Control Deck (Gallery Preview, Shutter/Record Button, Camera Switch)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Gallery Thumbnail / Quick Link
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(SurfaceDark)
                    .border(2.dp, SurfaceElevated, CircleShape)
                    .clickable { onGalleryClick() }
                    .testTag("gallery_thumbnail_button"),
                contentAlignment = Alignment.Center
            ) {
                val latest = uiState.latestCapture
                if (latest != null) {
                    val thumbFile = File(latest.thumbnailPath)
                    if (thumbFile.exists()) {
                        AsyncImage(
                            model = thumbFile,
                            contentDescription = "Last captured media",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Gallery",
                            tint = TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Format badge (RAW / VID / PNG)
                    val badge = when {
                        latest.mediaType == MediaType.VIDEO -> "VID"
                        latest.hasRaw -> "RAW"
                        else -> "PNG"
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .clip(RoundedCornerShape(topStart = 4.dp))
                            .background(
                                if (badge == "RAW") RawPurple else if (badge == "VID") RecordingRed else ElectricGreen
                            )
                            .padding(horizontal = 3.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = badge,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Gallery",
                        tint = TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Center: Large Shutter / Record Button
            ShutterButton(
                uiState = uiState,
                onClick = onShutterClick
            )

            // Right: Flip Camera (Rear <-> Front)
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(SurfaceDark)
                    .border(2.dp, SurfaceElevated, CircleShape)
                    .clickable(enabled = !uiState.isRecording) { onFlipCamera() }
                    .testTag("flip_camera_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FlipCameraAndroid,
                    contentDescription = "Switch Camera",
                    tint = if (uiState.isRecording) TextTertiary else TextPrimary,
                    modifier = Modifier.size(26.dp)
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
    val isPhotoMode = uiState.cameraMode == CameraMode.PHOTO
    val isRecording = uiState.isRecording

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
                color = ElectricGreen,
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
