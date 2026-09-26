package com.example.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.camera.model.CameraInfoModel
import com.example.ui.theme.*

@Composable
fun CameraTechnicalDetailsDialog(
    camera: CameraInfoModel?,
    onDismiss: () -> Unit
) {
    if (camera == null) return

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceElevated),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Camera Specifications",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = camera.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = ElectricCyan
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

                // Detail Rows
                SpecSectionHeader(title = "Hardware & Identification")
                SpecRow(label = "Camera ID", value = camera.cameraId)
                SpecRow(label = "Lens Facing", value = camera.facing.name)
                SpecRow(label = "Hardware Level", value = camera.hardwareLevelName)
                SpecRow(label = "Sensor Orientation", value = "${camera.sensorOrientation}°")
                SpecRow(label = "Is Logical Multi-Camera", value = if (camera.isLogicalMultiCamera) "Yes" else "No")
                if (camera.physicalCameraIds.isNotEmpty()) {
                    SpecRow(label = "Physical Sensor IDs", value = camera.physicalCameraIds.joinToString(", "))
                }

                Spacer(modifier = Modifier.height(12.dp))
                SpecSectionHeader(title = "Optics & Sensor")
                camera.sensorSize?.let { size ->
                    SpecRow(label = "Physical Sensor Size", value = "${size.width} mm × ${size.height} mm")
                }
                SpecRow(label = "Focal Length(s)", value = camera.focalLengths.joinToString(", ") { "${it}mm" })
                camera.equivalentFocalLength35mm?.let { eq ->
                    SpecRow(label = "35mm Equivalent", value = "${eq.toInt()} mm")
                }
                SpecRow(label = "Max Digital Zoom", value = "${String.format("%.1f", camera.maxDigitalZoom)}×")
                SpecRow(label = "Flash Available", value = if (camera.hasFlash) "Yes" else "No")
                SpecRow(label = "Optical Stabilization (OIS)", value = if (camera.supportsOpticalStabilization) "Supported" else "Not Supported")
                SpecRow(label = "Video Stabilization (EIS)", value = if (camera.supportsVideoStabilization) "Supported" else "Not Supported")

                Spacer(modifier = Modifier.height(12.dp))
                SpecSectionHeader(title = "Capture Formats & Pipeline")
                SpecRow(label = "Uncompressed Lossless (PNG)", value = "Supported (Direct YUV Stream)")
                SpecRow(label = "RAW Sensor Capture (DNG)", value = if (camera.supportsRaw) "Supported (DngCreator)" else "Not Available on this sensor")
                SpecRow(label = "Max Still Resolution", value = "${camera.maxPhotoSize.width} × ${camera.maxPhotoSize.height} (${(camera.maxPhotoSize.width * camera.maxPhotoSize.height / 1_000_000.0).toInt()} MP)")
                
                val videoSizesStr = camera.supportedVideoSizes.take(3).joinToString(", ") { "${it.width}x${it.height}" }
                SpecRow(label = "Top Video Resolutions", value = if (videoSizesStr.isNotEmpty()) videoSizesStr else "1080p, 720p")

                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close", color = TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun SpecSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = CyanGaze,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}
