package com.example.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.camera.model.CameraInfoModel
import com.example.camera.model.LensFacingType
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraSelectionBottomSheet(
    cameras: List<CameraInfoModel>,
    selectedCamera: CameraInfoModel?,
    onCameraSelect: (CameraInfoModel) -> Unit,
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
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Discovered Cameras",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${cameras.size} camera device(s) exposed via Camera2",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // OEM Limitation Notice
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SurfaceElevated.copy(alpha = 0.6f),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = CyanGaze,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Hardware Discovery & OEM Access",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Private Camera shows every physical and logical camera exposed through Android's public Camera2 APIs. Some phone manufacturers restrict certain auxiliary lenses to their factory app.",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Camera List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(cameras) { camera ->
                    val isSelected = selectedCamera?.cameraId == camera.cameraId
                    CameraListItem(
                        camera = camera,
                        isSelected = isSelected,
                        onClick = { onCameraSelect(camera) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraListItem(
    camera: CameraInfoModel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) SurfaceElevated else SurfaceCard,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) ElectricCyan else SurfaceElevated
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("camera_item_${camera.cameraId}")
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Info
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon indicator
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) ElectricCyan.copy(alpha = 0.2f) else DeepObsidian),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (camera.facing) {
                            LensFacingType.FRONT -> Icons.Default.AccountCircle
                            LensFacingType.BACK -> Icons.Default.CameraAlt
                            LensFacingType.EXTERNAL -> Icons.Default.Videocam
                        },
                        contentDescription = null,
                        tint = if (isSelected) ElectricCyan else TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = camera.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) ElectricCyan else TextPrimary
                        )

                        // Hardware Level badge
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = DeepObsidian,
                            modifier = Modifier.padding(start = 2.dp)
                        ) {
                            Text(
                                text = "ID: ${camera.cameraId}",
                                fontSize = 10.sp,
                                color = TextTertiary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Specs summary
                    val specs = buildString {
                        camera.equivalentFocalLength35mm?.let {
                            append("${it.toInt()}mm eq • ")
                        }
                        append("${camera.maxPhotoSize.width}x${camera.maxPhotoSize.height}")
                        if (camera.supportsRaw) {
                            append(" • RAW")
                        }
                    }
                    Text(
                        text = specs,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    // Capabilities pill row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        if (camera.supportsRaw) {
                            CapabilityBadge(label = "RAW DNG", color = RawPurple)
                        }
                        if (camera.isLogicalMultiCamera) {
                            CapabilityBadge(label = "Multi-Lens", color = CyanGaze)
                        }
                        if (camera.hasFlash) {
                            CapabilityBadge(label = "Flash", color = WarningAmber)
                        }
                        if (camera.supportsOpticalStabilization || camera.supportsVideoStabilization) {
                            CapabilityBadge(label = "Stabilized", color = SuccessGreen)
                        }
                    }
                }
            }

            // Right selection radio / check
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = ElectricCyan,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun CapabilityBadge(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f),
        modifier = Modifier.height(18.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}
