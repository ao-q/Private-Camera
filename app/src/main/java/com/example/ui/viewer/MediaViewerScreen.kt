package com.example.ui.viewer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.MediaItemEntity
import com.example.data.local.MediaType
import com.example.data.repository.ShareOption
import com.example.data.repository.StorageMonitor
import com.example.ui.theme.*
import com.example.ui.viewmodel.MediaViewerViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    mediaId: String,
    viewModel: MediaViewerViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showControls by remember { mutableStateOf(true) }

    // Export SAF contract launcher
    var pendingExportSourcePath by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri: Uri? ->
        if (uri != null && pendingExportSourcePath != null) {
            viewModel.exportToDestination(pendingExportSourcePath!!, uri)
        }
    }

    LaunchedEffect(mediaId) {
        viewModel.loadMedia(mediaId)
    }

    LaunchedEffect(uiState.mediaItem) {
        val item = uiState.mediaItem
        if (item != null && item.mediaType == MediaType.VIDEO && item.videoPath != null) {
            viewModel.initializeVideoPlayer(context, item.videoPath)
        }
    }

    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) {
            onNavigateBack()
        }
    }

    BackHandler {
        viewModel.releasePlayer()
        onNavigateBack()
    }

    val item = uiState.mediaItem

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepObsidian)
    ) {
        if (item != null) {
            // Main Media Content (Photo with Zoom or Video Player)
            if (item.mediaType == MediaType.PHOTO) {
                ZoomablePhotoViewer(
                    imagePath = item.pngPath ?: "",
                    onTap = { showControls = !showControls }
                )
            } else {
                VideoPlayerContent(
                    videoPath = item.videoPath ?: "",
                    isPlaying = uiState.isPlaying,
                    onTap = { showControls = !showControls }
                )
            }

            // Top Bar
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
            ) {
                TopAppBar(
                    title = {
                        Column {
                            val timeStr = SimpleDateFormat("MMM d, yyyy • HH:mm:ss", Locale.getDefault())
                                .format(Date(item.timestamp))
                            Text(
                                text = item.cameraLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = timeStr,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                viewModel.releasePlayer()
                                onNavigateBack()
                            },
                            modifier = Modifier.testTag("viewer_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }
                    },
                    actions = {
                        // Details Info
                        IconButton(
                            onClick = { viewModel.openDetails() },
                            modifier = Modifier.testTag("media_details_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = "Technical Metadata",
                                tint = TextPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = OverlayScrim
                    )
                )
            }

            // Bottom Controls Bar
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                Surface(
                    color = OverlayScrim,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Video playback scrub bar
                        if (item.mediaType == MediaType.VIDEO && uiState.durationMs > 0) {
                            VideoScrubBar(
                                currentPos = uiState.currentPositionMs,
                                duration = uiState.durationMs,
                                isPlaying = uiState.isPlaying,
                                isMuted = uiState.isMuted,
                                onPlayPauseToggle = { viewModel.togglePlayPause() },
                                onSeek = { pos -> viewModel.seekTo(pos) },
                                onMuteToggle = { viewModel.toggleMute() }
                            )
                        }

                        // Bottom Actions: Share, Export, Delete
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Share Button
                            FilledTonalButton(
                                onClick = {
                                    if (item.hasRaw) {
                                        viewModel.openShareDialog()
                                    } else {
                                        val intent = viewModel.createShareIntent(ShareOption.PNG_ONLY)
                                        intent?.let { context.startActivity(it) }
                                    }
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = SurfaceElevated),
                                modifier = Modifier.testTag("share_media_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share")
                            }

                            // Export to Device Button
                            FilledTonalButton(
                                onClick = {
                                    val source = item.pngPath ?: item.videoPath
                                    if (source != null) {
                                        pendingExportSourcePath = source
                                        val extension = if (item.mediaType == MediaType.PHOTO) "png" else "mp4"
                                        exportLauncher.launch("Private_${System.currentTimeMillis()}.$extension")
                                    }
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = SurfaceElevated),
                                modifier = Modifier.testTag("export_media_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.FileDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export")
                            }

                            // Delete Button
                            IconButton(
                                onClick = { viewModel.openDeleteDialog() },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceElevated)
                                    .testTag("delete_media_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Delete item",
                                    tint = RecordingRed
                                )
                            }
                        }
                    }
                }
            }

            // Export Success Snackbar
            uiState.exportSuccessMessage?.let { msg ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 100.dp, start = 16.dp, end = 16.dp),
                    containerColor = SurfaceElevated,
                    action = {
                        TextButton(onClick = { viewModel.clearExportMessage() }) {
                            Text("OK", color = ElectricCyan)
                        }
                    }
                ) {
                    Text(msg, color = TextPrimary)
                }
            }

            // Share RAW / PNG Option Dialog
            if (uiState.showShareDialog) {
                ShareFormatDialog(
                    onOptionSelected = { option ->
                        viewModel.closeShareDialog()
                        val intent = viewModel.createShareIntent(option)
                        intent?.let { context.startActivity(it) }
                    },
                    onDismiss = { viewModel.closeShareDialog() }
                )
            }

            // Technical Metadata Sheet
            if (uiState.showDetailsModal) {
                MediaMetadataBottomSheet(
                    item = item,
                    onDismiss = { viewModel.closeDetails() }
                )
            }

            // Delete Confirmation Dialog
            if (uiState.showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { viewModel.closeDeleteDialog() },
                    title = {
                        Text(
                            text = "Delete Private Media?",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    },
                    text = {
                        Text(
                            text = "This will permanently remove the lossless PNG, RAW DNG, and thumbnail files from app-private storage. This cannot be undone.",
                            color = TextSecondary
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.confirmDelete() },
                            colors = ButtonDefaults.buttonColors(containerColor = RecordingRed)
                        ) {
                            Text("Delete Permanently", color = TextPrimary)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.closeDeleteDialog() }) {
                            Text("Cancel", color = TextSecondary)
                        }
                    },
                    containerColor = SurfaceDark
                )
            }
        }
    }
}

@Composable
private fun ZoomablePhotoViewer(
    imagePath: String,
    onTap: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val file = File(imagePath)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        scale = if (scale > 1.5f) 1f else 2.5f
                        offset = Offset.Zero
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale > 1f) {
                        offset = Offset(
                            x = offset.x + pan.x,
                            y = offset.y + pan.y
                        )
                    } else {
                        offset = Offset.Zero
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (file.exists()) {
            AsyncImage(
                model = file,
                contentDescription = "Lossless Fullscreen Photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )
        } else {
            Text("Image file not found on disk", color = TextSecondary)
        }
    }
}

@Composable
private fun VideoPlayerContent(
    videoPath: String,
    isPlaying: Boolean,
    onTap: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onTap() },
        contentAlignment = Alignment.Center
    ) {
        // Video View
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                SurfaceView(ctx)
            }
        )
    }
}

@Composable
private fun VideoScrubBar(
    currentPos: Int,
    duration: Int,
    isPlaying: Boolean,
    isMuted: Boolean,
    onPlayPauseToggle: () -> Unit,
    onSeek: (Int) -> Unit,
    onMuteToggle: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val curSecs = currentPos / 1000
            val durSecs = duration / 1000
            val curStr = String.format("%02d:%02d", curSecs / 60, curSecs % 60)
            val durStr = String.format("%02d:%02d", durSecs / 60, durSecs % 60)

            Text(curStr, fontSize = 11.sp, color = TextSecondary)
            Text(durStr, fontSize = 11.sp, color = TextSecondary)
        }

        Slider(
            value = currentPos.toFloat(),
            onValueChange = { onSeek(it.toInt()) },
            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
            colors = SliderDefaults.colors(
                thumbColor = ElectricCyan,
                activeTrackColor = ElectricCyan,
                inactiveTrackColor = SurfaceElevated
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMuteToggle) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = "Mute",
                    tint = TextPrimary
                )
            }

            IconButton(
                onClick = onPlayPauseToggle,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(ElectricCyan)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = DeepObsidian,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaMetadataBottomSheet(
    item: MediaItemEntity,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        dragHandle = { BottomSheetDefaults.DragHandle(color = SurfaceElevated) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Technical Metadata",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            MetadataRow("Media Type", item.mediaType.name)
            MetadataRow("Camera ID", item.cameraId)
            MetadataRow("Lens", item.cameraLabel)
            MetadataRow("Dimensions", "${item.width} × ${item.height} px")
            MetadataRow("PNG File Size", StorageMonitor.formatBytes(item.fileSizeBytes))

            if (item.hasRaw && item.dngSizeBytes != null) {
                MetadataRow("RAW DNG Size", StorageMonitor.formatBytes(item.dngSizeBytes))
                MetadataRow("Sensor Master Format", "Adobe DNG (Uncompressed Bayer)")
            }

            item.iso?.let { MetadataRow("ISO Sensitivity", "$it") }
            item.exposureTime?.let { MetadataRow("Exposure Time", it) }
            item.focalLength?.let { MetadataRow("Focal Length", "${it}mm") }
            item.aperture?.let { MetadataRow("Aperture", "f/$it") }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "SANDBOXED STORAGE PATHS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CyanGaze
            )
            item.pngPath?.let { MetadataRow("PNG Path", it) }
            item.dngPath?.let { MetadataRow("DNG Path", it) }
            item.videoPath?.let { MetadataRow("Video Path", it) }
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(label, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = TextPrimary,
            modifier = Modifier.weight(1.2f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
private fun ShareFormatDialog(
    onOptionSelected: (ShareOption) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Share Photo Format",
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "This photo was captured with both a lossless PNG and a master RAW (DNG) sensor file. Choose how you'd like to share it:",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = { onOptionSelected(ShareOption.PNG_ONLY) },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Share Lossless PNG (Universal)", color = TextPrimary)
                }
                Button(
                    onClick = { onOptionSelected(ShareOption.RAW_DNG_ONLY) },
                    colors = ButtonDefaults.buttonColors(containerColor = RawPurple.copy(alpha = 0.8f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Share Master RAW (.dng)", color = TextPrimary)
                }
                Button(
                    onClick = { onOptionSelected(ShareOption.BOTH_PNG_AND_RAW) },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Share Both (PNG + RAW)", color = TextPrimary)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = SurfaceDark
    )
}
