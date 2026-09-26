package com.example.ui.camera

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.camera.session.CameraSessionController

@Composable
fun CameraPreviewView(
    sessionController: CameraSessionController,
    currentZoomRatio: Float,
    onZoomChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                SurfaceView(ctx).apply {
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
                        scaleGestureDetector.onTouchEvent(event)
                        true
                    }

                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            sessionController.onPreviewSurfaceAvailable(holder.surface)
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int
                        ) {
                            sessionController.onPreviewSurfaceAvailable(holder.surface)
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            sessionController.onPreviewSurfaceDestroyed()
                        }
                    })
                }
            },
            update = {
                // Update callbacks if needed
            }
        )
    }
}
