package com.example.ui.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.camera.session.FlashMode
import com.example.data.repository.StorageInfo
import com.example.data.repository.StorageMonitor
import com.example.ui.theme.*
import com.example.ui.viewmodel.CameraMode
import com.example.ui.viewmodel.CameraUiState

@Composable
fun CameraTopBar(
    uiState: CameraUiState,
    onFlashClick: () -> Unit,
    onRawToggle: () -> Unit,
    onResolutionSelect: (String) -> Unit,
    onMicToggle: () -> Unit,
    onInfoClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showResolutionMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left actions: Flash & RAW badge
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flash Mode
            IconButton(
                onClick = onFlashClick,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(OverlayLightScrim)
                    .testTag("flash_toggle_button")
            ) {
                val (icon, tint) = when (uiState.flashMode) {
                    FlashMode.OFF -> Icons.Outlined.FlashOff to TextSecondary
                    FlashMode.AUTO -> Icons.Filled.FlashAuto to WarningAmber
                    FlashMode.ON -> Icons.Filled.FlashOn to WarningAmber
                    FlashMode.TORCH -> Icons.Filled.Highlight to ElectricCyan
                }
                Icon(
                    imageVector = icon,
                    contentDescription = "Flash mode: ${uiState.flashMode}",
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }

            // RAW Badge in Photo Mode
            if (uiState.cameraMode == CameraMode.PHOTO) {
                val hasCameraRaw = uiState.selectedCamera?.supportsRaw == true
                Surface(
                    onClick = onRawToggle,
                    shape = RoundedCornerShape(16.dp),
                    color = if (hasCameraRaw && uiState.alsoSaveRaw) RawPurple.copy(alpha = 0.85f) else OverlayLightScrim,
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("raw_toggle_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (hasCameraRaw) (if (uiState.alsoSaveRaw) "PNG+RAW" else "PNG") else "PNG",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hasCameraRaw && uiState.alsoSaveRaw) TextPrimary else TextSecondary
                        )
                        if (!hasCameraRaw) {
                            Text(
                                text = "ONLY",
                                fontSize = 9.sp,
                                color = TextTertiary
                            )
                        }
                    }
                }
            }

            // Video Controls in Video Mode
            if (uiState.cameraMode == CameraMode.VIDEO) {
                // Resolution Selector Dropdown
                Box {
                    Surface(
                        onClick = { showResolutionMenu = true },
                        shape = RoundedCornerShape(16.dp),
                        color = ElectricCyan.copy(alpha = 0.2f),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("video_resolution_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = uiState.selectedVideoResolution,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricCyan
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select Resolution",
                                tint = ElectricCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showResolutionMenu,
                        onDismissRequest = { showResolutionMenu = false },
                        modifier = Modifier.background(SurfaceDark)
                    ) {
                        val resolutions = listOf("4K", "1080p", "720p")
                        resolutions.forEach { res ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = res,
                                        fontWeight = if (uiState.selectedVideoResolution == res) FontWeight.Bold else FontWeight.Normal,
                                        color = if (uiState.selectedVideoResolution == res) ElectricCyan else TextPrimary
                                    )
                                },
                                onClick = {
                                    onResolutionSelect(res)
                                    showResolutionMenu = false
                                }
                            )
                        }
                    }
                }

                // Microphone toggle
                IconButton(
                    onClick = onMicToggle,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (uiState.isAudioEnabled) OverlayLightScrim else RecordingRed.copy(alpha = 0.3f))
                        .testTag("mic_toggle_button")
                ) {
                    Icon(
                        imageVector = if (uiState.isAudioEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = if (uiState.isAudioEnabled) "Microphone On" else "Microphone Muted",
                        tint = if (uiState.isAudioEnabled) ElectricCyan else RecordingRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Right actions: Storage space indicator, Info, Settings
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Remaining Storage Pill
            uiState.storageInfo?.let { storage ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (storage.isLowStorage) RecordingRed.copy(alpha = 0.8f) else OverlayLightScrim,
                    modifier = Modifier.height(34.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SdStorage,
                            contentDescription = "Storage",
                            tint = if (storage.isLowStorage) TextPrimary else TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        val summaryText = if (uiState.cameraMode == CameraMode.PHOTO) {
                            "${StorageMonitor.formatBytes(storage.availableBytes)} • ~${storage.estimatedPhotosRemaining}"
                        } else {
                            "${StorageMonitor.formatBytes(storage.availableBytes)} • ~${storage.estimatedVideoMinutesRemaining}m"
                        }
                        Text(
                            text = summaryText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (storage.isLowStorage) TextPrimary else TextSecondary
                        )
                    }
                }
            }

            // Camera Sensor Info
            IconButton(
                onClick = onInfoClick,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(OverlayLightScrim)
                    .testTag("camera_info_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Camera hardware specifications",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Settings
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(OverlayLightScrim)
                    .testTag("camera_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Application settings",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
