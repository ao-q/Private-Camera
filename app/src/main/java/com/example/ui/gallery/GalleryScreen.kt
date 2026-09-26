package com.example.ui.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.MediaItemEntity
import com.example.data.local.MediaType
import com.example.ui.theme.*
import com.example.ui.viewmodel.GalleryViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    onNavigateBack: () -> Unit,
    onMediaClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    BackHandler {
        if (uiState.isSelectionMode) {
            viewModel.clearSelection()
        } else {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.isSelectionMode) {
                        Text(
                            text = "${uiState.selectedIds.size} Selected",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                    } else {
                        Column {
                            Text(
                                text = "Private Gallery",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "${uiState.mediaItems.size} item(s) • App Private Storage",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (uiState.isSelectionMode) viewModel.clearSelection()
                            else onNavigateBack()
                        },
                        modifier = Modifier.testTag("gallery_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    if (uiState.isSelectionMode) {
                        // Select All
                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(
                                imageVector = Icons.Default.SelectAll,
                                contentDescription = "Select All",
                                tint = TextPrimary
                            )
                        }
                        // Batch Share
                        IconButton(
                            onClick = {
                                val intent = viewModel.createShareIntentForSelected(context)
                                intent?.let { context.startActivity(it) }
                            },
                            enabled = uiState.selectedIds.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = "Share selected items",
                                tint = if (uiState.selectedIds.isNotEmpty()) ElectricCyan else TextTertiary
                            )
                        }
                        // Batch Delete
                        IconButton(
                            onClick = { viewModel.promptDeleteSelected() },
                            enabled = uiState.selectedIds.isNotEmpty(),
                            modifier = Modifier.testTag("batch_delete_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Delete selected items",
                                tint = if (uiState.selectedIds.isNotEmpty()) RecordingRed else TextTertiary
                            )
                        }
                    } else if (uiState.mediaItems.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.toggleSelectionMode() },
                            modifier = Modifier.testTag("select_mode_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Checklist,
                                contentDescription = "Select multiple",
                                tint = TextPrimary
                            )
                        }
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.mediaItems.isEmpty()) {
                EmptyGalleryView()
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = uiState.mediaItems,
                        key = { it.id }
                    ) { item ->
                        val isSelected = uiState.selectedIds.contains(item.id)
                        GalleryGridItem(
                            item = item,
                            isSelectionMode = uiState.isSelectionMode,
                            isSelected = isSelected,
                            onClick = {
                                if (uiState.isSelectionMode) {
                                    viewModel.toggleItemSelection(item.id)
                                } else {
                                    onMediaClick(item.id)
                                }
                            },
                            onLongClick = {
                                if (!uiState.isSelectionMode) {
                                    viewModel.toggleSelectionMode()
                                    viewModel.toggleItemSelection(item.id)
                                }
                            }
                        )
                    }
                }
            }

            // Delete Confirmation Dialog
            if (uiState.showDeleteConfirmation) {
                AlertDialog(
                    onDismissRequest = { viewModel.cancelDelete() },
                    title = {
                        Text(
                            text = "Delete ${uiState.selectedIds.size} Item(s)?",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    },
                    text = {
                        Text(
                            text = "This will permanently delete the selected photos, lossless PNGs, RAW DNGs, and videos from private storage. This action cannot be undone.",
                            color = TextSecondary
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.confirmDeleteSelected() },
                            colors = ButtonDefaults.buttonColors(containerColor = RecordingRed),
                            modifier = Modifier.testTag("confirm_delete_button")
                        ) {
                            Text("Delete Permanently", color = TextPrimary)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.cancelDelete() }) {
                            Text("Cancel", color = TextSecondary)
                        }
                    },
                    containerColor = SurfaceDark
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryGridItem(
    item: MediaItemEntity,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceCard)
            .border(
                width = if (isSelected) 2.5.dp else 0.dp,
                color = if (isSelected) ElectricCyan else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("gallery_item_${item.id}"),
        contentAlignment = Alignment.Center
    ) {
        val thumbFile = File(item.thumbnailPath)
        if (thumbFile.exists()) {
            AsyncImage(
                model = thumbFile,
                contentDescription = "Media item captured with ${item.cameraLabel}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = if (item.mediaType == MediaType.VIDEO) Icons.Default.Videocam else Icons.Default.Image,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(32.dp)
            )
        }

        // Top-left Badges (PNG vs PNG + RAW)
        if (item.mediaType == MediaType.PHOTO) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(DeepObsidian.copy(alpha = 0.8f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (item.hasRaw) "PNG + RAW" else "PNG",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (item.hasRaw) RawPurple else ElectricCyan
                )
            }
        }

        // Video Duration Overlay
        if (item.mediaType == MediaType.VIDEO) {
            val totalSecs = (item.durationMs ?: 0L) / 1000
            val mins = totalSecs / 60
            val secs = totalSecs % 60
            val durStr = String.format("%02d:%02d", mins, secs)

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(DeepObsidian.copy(alpha = 0.85f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = RecordingRed,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = durStr,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
        }

        // Selection Checkbox Overlay
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) ElectricCyan else DeepObsidian.copy(alpha = 0.7f))
                    .border(1.5.dp, if (isSelected) ElectricCyan else Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = DeepObsidian,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyGalleryView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            tint = CyanGaze,
            modifier = Modifier.size(56.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Your Private Vault is Empty",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Photos and videos captured with Private Camera are stored in your app-private sandboxed directory. They never leak to the system photo library.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
