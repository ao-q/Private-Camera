package com.example.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.repository.AppSettings
import com.example.data.repository.SettingsRepository
import com.example.data.repository.StorageInfo
import com.example.data.repository.StorageMonitor
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    storageMonitor: StorageMonitor,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by settingsRepository.settings.collectAsStateWithLifecycle()
    var storageInfo by remember { mutableStateOf<StorageInfo?>(null) }

    LaunchedEffect(Unit) {
        storageInfo = storageMonitor.checkStorage()
    }

    BackHandler {
        onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings & Privacy",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceDark
                )
            )
        },
        containerColor = DeepObsidian,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Privacy Architecture Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceElevated.copy(alpha = 0.7f),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyanSubtle.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Security,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(28.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "100% Offline & Private Sandbox",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "Private Camera operates completely offline with zero tracking, no cloud telemetry, and no account requirements. All photos and videos remain exclusively within your sandboxed app storage unless you explicitly export or share them.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            // Photo Capture Settings
            SettingsSectionHeader(title = "Photo Capture & RAW Master")
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SurfaceCard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Also save RAW when available",
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Saves uncompressed Adobe DNG sensor masters alongside lossless PNG files on RAW-capable cameras.",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = settings.alsoSaveRaw,
                            onCheckedChange = { settingsRepository.setAlsoSaveRaw(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DeepObsidian,
                                checkedTrackColor = ElectricCyan
                            ),
                            modifier = Modifier.testTag("save_raw_switch")
                        )
                    }

                    HorizontalDivider(
                        color = SurfaceElevated,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Lossless PNG Encoding",
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Direct YUV-to-PNG stream. No lossy JPEG compression is ever performed.",
                                fontSize = 12.sp,
                                color = CyanGaze
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = SuccessGreen
                        )
                    }
                }
            }

            // Video Recording Settings
            SettingsSectionHeader(title = "Video Recording & Background Service")
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SurfaceCard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Microphone Audio Default",
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Enable microphone recording for video by default.",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = settings.enableMicrophoneAudio,
                            onCheckedChange = { settingsRepository.setEnableMicrophoneAudio(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DeepObsidian,
                                checkedTrackColor = ElectricCyan
                            )
                        )
                    }

                    HorizontalDivider(
                        color = SurfaceElevated,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Video Stabilization",
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Activate hardware EIS stabilization when supported by the active camera.",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = settings.enableStabilization,
                            onCheckedChange = { settingsRepository.setEnableStabilization(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DeepObsidian,
                                checkedTrackColor = ElectricCyan
                            )
                        )
                    }
                }
            }

            // Continuous Background Recording Guide
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SurfaceElevated.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LockClock,
                            contentDescription = null,
                            tint = WarningAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Lock-Screen Background Recording",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = "When you press Record, a persistent foreground service maintains the camera and encoder session. You can lock your device or switch apps; the recording continues uninterrupted until you tap Stop in the app or notification.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }

            // Storage Breakdown Card
            storageInfo?.let { storage ->
                SettingsSectionHeader(title = "App-Private Storage Usage")
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SurfaceCard,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StorageUsageRow(
                            label = "Lossless Photos (PNG)",
                            usage = StorageMonitor.formatBytes(storage.photosUsageBytes)
                        )
                        StorageUsageRow(
                            label = "Master RAW Sensor Files (DNG)",
                            usage = StorageMonitor.formatBytes(storage.rawUsageBytes)
                        )
                        StorageUsageRow(
                            label = "Videos (MP4)",
                            usage = StorageMonitor.formatBytes(storage.videosUsageBytes)
                        )
                        HorizontalDivider(
                            color = SurfaceElevated,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        StorageUsageRow(
                            label = "Available Free Storage",
                            usage = StorageMonitor.formatBytes(storage.availableBytes),
                            highlight = true
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = CyanGaze,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun StorageUsageRow(
    label: String,
    usage: String,
    highlight: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = if (highlight) TextPrimary else TextSecondary,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal
        )
        Text(
            text = usage,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (highlight) ElectricCyan else TextPrimary
        )
    }
}
