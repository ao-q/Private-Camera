package com.example.ui.camera

import android.content.res.Configuration
import android.util.Size
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.camera.model.CameraInfoModel
import com.example.camera.session.CameraSessionController
import com.example.ui.theme.DeepObsidian
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricGreen
import com.example.ui.theme.OverlayScrim
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun CameraPreviewView(
    sessionController: CameraSessionController,
    currentCamera: CameraInfoModel?,
    currentZoomRatio: Float,
    onZoomChanged: (Float) -> Unit,
    onTapToFocus: (normX: Float, normY: Float, viewWidth: Float, viewHeight: Float) -> Unit,
    activeFocusPoint: Pair<Float, Float>?,
    exposureCompensation: Float,
    onExposureChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Select camera's optimal preview size (e.g. 1440x1080 for 4:3 or 1920x1080 for 16:9)
    val previewSize = currentCamera?.optimalPreviewSize ?: Size(1440, 1080)

    // Calculate aspect ratio dynamically so preview is NEVER stretched or distorted!
    // In portrait: camera sensor is rotated (90 or 270 deg), so display aspect ratio is height/width (e.g. 1080/1440 = 3:4)
    // In landscape: display aspect ratio is width/height (e.g. 1440/1080 = 4:3)
    val targetAspectRatio = if (isLandscape) {
        previewSize.width.toFloat() / previewSize.height.toFloat()
    } else {
        previewSize.height.toFloat() / previewSize.width.toFloat()
    }

    var previewWidthPx by remember { mutableFloatStateOf(1f) }
    var previewHeightPx by remember { mutableFloatStateOf(1f) }

    // Tap-to-focus ring auto-dismiss timer
    var reticleVisible by remember { mutableStateOf(false) }
    LaunchedEffect(activeFocusPoint) {
        if (activeFocusPoint != null) {
            reticleVisible = true
            delay(3500)
            reticleVisible = false
        } else {
            reticleVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepObsidian),
        contentAlignment = Alignment.Center
    ) {
        // Aspect-ratio-constrained container that guarantees zero distortion and zero stretching!
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(targetAspectRatio)
                .clipToBounds()
                .onGloballyPositioned { coordinates ->
                    if (coordinates.size.width > 0 && coordinates.size.height > 0) {
                        previewWidthPx = coordinates.size.width.toFloat()
                        previewHeightPx = coordinates.size.height.toFloat()
                    }
                }
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        // Set buffer size to camera's optimal preview size for hardware GPU acceleration
                        holder.setFixedSize(previewSize.width, previewSize.height)

                        val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                            override fun onSingleTapUp(e: MotionEvent): Boolean {
                                val w = width.toFloat().coerceAtLeast(1f)
                                val h = height.toFloat().coerceAtLeast(1f)
                                val normX = (e.x / w).coerceIn(0f, 1f)
                                val normY = (e.y / h).coerceIn(0f, 1f)
                                onTapToFocus(normX, normY, w, h)
                                return true
                            }
                        })

                        val scaleGestureDetector = ScaleGestureDetector(
                            ctx,
                            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                                var currentScale = currentZoomRatio
                                override fun onScale(detector: ScaleGestureDetector): Boolean {
                                    currentScale *= detector.scaleFactor
                                    onZoomChanged(currentScale)
                                    return true
                                }
                            }
                        )

                        setOnTouchListener { _, event ->
                            val scaleHandled = scaleGestureDetector.onTouchEvent(event)
                            val tapHandled = gestureDetector.onTouchEvent(event)
                            scaleHandled || tapHandled || true
                        }

                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                holder.setFixedSize(previewSize.width, previewSize.height)
                                sessionController.onPreviewSurfaceAvailable(holder.surface)
                            }

                            override fun surfaceChanged(
                                holder: SurfaceHolder,
                                format: Int,
                                width: Int,
                                height: Int
                            ) {
                                holder.setFixedSize(previewSize.width, previewSize.height)
                                sessionController.onPreviewSurfaceAvailable(holder.surface)
                            }

                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                sessionController.onPreviewSurfaceDestroyed()
                            }
                        })
                    }
                },
                update = { surfaceView ->
                    surfaceView.holder.setFixedSize(previewSize.width, previewSize.height)
                }
            )

            // Animated Tap-to-Focus Reticle & Quick Exposure Slider
            if (reticleVisible && activeFocusPoint != null) {
                FocusMeteringReticle(
                    focusX = activeFocusPoint.first,
                    focusY = activeFocusPoint.second,
                    exposureCompensation = exposureCompensation,
                    onExposureChanged = { delta ->
                        val step = currentCamera?.aeCompensationStep ?: 0.333f
                        onExposureChanged(exposureCompensation + delta * step * 0.1f)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun FocusMeteringReticle(
    focusX: Float,
    focusY: Float,
    exposureCompensation: Float,
    onExposureChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = remember { Animatable(1.3f) }

    LaunchedEffect(focusX, focusY) {
        scale.snapTo(1.35f)
        scale.animateTo(1.0f, animationSpec = tween(durationMillis = 220))
    }

    Box(
        modifier = modifier
            .pointerInput(focusX, focusY) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    // Drag up increases EV, drag down decreases EV
                    onExposureChanged(-dragAmount * 0.08f)
                }
            }
    ) {
        val density = LocalDensity.current
        val reticleSizeDp = 68.dp
        val halfSizePx = with(density) { (reticleSizeDp / 2).toPx() }

        val reticleLeft = (focusX - halfSizePx).coerceAtLeast(8f)
        val reticleTop = (focusY - halfSizePx).coerceAtLeast(8f)

        // Focus Reticle Ring + Corner Brackets
        Box(
            modifier = Modifier
                .offset { IntOffset(reticleLeft.toInt(), reticleTop.toInt()) }
                .size(reticleSizeDp)
                .scale(scale.value)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 2.dp.toPx()
                val cornerLength = 14.dp.toPx()
                val w = size.width
                val h = size.height
                val reticleColor = ElectricGreen

                // Outer soft circle
                drawCircle(
                    color = reticleColor.copy(alpha = 0.25f),
                    radius = w / 2,
                    center = Offset(w / 2, h / 2),
                    style = Stroke(width = 1.dp.toPx())
                )

                // Top-Left bracket
                drawLine(reticleColor, Offset(0f, 0f), Offset(cornerLength, 0f), strokeWidth)
                drawLine(reticleColor, Offset(0f, 0f), Offset(0f, cornerLength), strokeWidth)

                // Top-Right bracket
                drawLine(reticleColor, Offset(w, 0f), Offset(w - cornerLength, 0f), strokeWidth)
                drawLine(reticleColor, Offset(w, 0f), Offset(w, cornerLength), strokeWidth)

                // Bottom-Left bracket
                drawLine(reticleColor, Offset(0f, h), Offset(cornerLength, h), strokeWidth)
                drawLine(reticleColor, Offset(0f, h), Offset(0f, h - cornerLength), strokeWidth)

                // Bottom-Right bracket
                drawLine(reticleColor, Offset(w, h), Offset(w - cornerLength, h), strokeWidth)
                drawLine(reticleColor, Offset(w, h), Offset(w, h - cornerLength), strokeWidth)

                // Center tiny crosshair dot
                drawCircle(
                    color = reticleColor,
                    radius = 2.dp.toPx(),
                    center = Offset(w / 2, h / 2)
                )
            }
        }

        // Mini Exposure Sun Slider next to the reticle
        val sunOffsetLeft = (reticleLeft + with(density) { reticleSizeDp.toPx() } + 12f)
        val sunOffsetTop = (focusY - with(density) { 36.dp.toPx() })

        Row(
            modifier = Modifier
                .offset { IntOffset(sunOffsetLeft.toInt(), sunOffsetTop.toInt()) }
                .clip(RoundedCornerShape(12.dp))
                .background(OverlayScrim)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.WbSunny,
                contentDescription = "Exposure Value",
                tint = ElectricGreen,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = if (exposureCompensation >= 0f) String.format("+%.1f", exposureCompensation) else String.format("%.1f", exposureCompensation),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
    }
}
