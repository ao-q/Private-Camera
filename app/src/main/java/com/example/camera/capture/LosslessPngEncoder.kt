package com.example.camera.capture

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.media.Image
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

object LosslessPngEncoder {

    /**
     * Converts an uncompressed YUV_420_888 Camera2 Image directly into a lossless PNG file.
     * ZERO lossy JPEG compression is used in this pipeline.
     */
    suspend fun encodeYuvToPng(
        image: Image,
        destinationFile: File,
        rotationDegrees: Int,
        mirrorHorizontal: Boolean
    ): Boolean = withContext(Dispatchers.Default) {
        try {
            val width = image.width
            val height = image.height
            val bitmap = yuv420ToRgbBitmap(image)

            val orientedBitmap = if (rotationDegrees != 0 || mirrorHorizontal) {
                val matrix = Matrix()
                if (rotationDegrees != 0) {
                    matrix.postRotate(rotationDegrees.toFloat())
                }
                if (mirrorHorizontal) {
                    matrix.postScale(-1f, 1f)
                }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
                if (rotated != bitmap) {
                    bitmap.recycle()
                }
                rotated
            } else {
                bitmap
            }

            FileOutputStream(destinationFile).use { fos ->
                // PNG is inherently lossless compression in Android (quality parameter is ignored or lossless)
                orientedBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                fos.flush()
            }
            orientedBitmap.recycle()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * High-speed direct software conversion of YUV_420_888 planes into an ARGB_8888 Bitmap.
     */
    private fun yuv420ToRgbBitmap(image: Image): Bitmap {
        val width = image.width
        val height = image.height

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer: ByteBuffer = yPlane.buffer
        val uBuffer: ByteBuffer = uPlane.buffer
        val vBuffer: ByteBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride

        val outPixels = IntArray(width * height)

        var outIndex = 0
        for (y in 0 until height) {
            val yRowStart = y * yRowStride
            val uvRowStart = (y shr 1) * uRowStride

            for (x in 0 until width) {
                val yIndex = yRowStart + x * yPixelStride
                val yVal = (yBuffer.get(yIndex).toInt() and 0xFF)

                val uvIndex = (y shr 1) * uRowStride + (x shr 1) * uPixelStride
                val vIndex = (y shr 1) * vRowStride + (x shr 1) * vPixelStride

                val uVal = (uBuffer.get(uvIndex).toInt() and 0xFF) - 128
                val vVal = (vBuffer.get(vIndex).toInt() and 0xFF) - 128

                // ITU-R BT.601 color conversion
                var r = (yVal + 1.402f * vVal).toInt()
                var g = (yVal - 0.344136f * uVal - 0.714136f * vVal).toInt()
                var b = (yVal + 1.772f * uVal).toInt()

                r = r.coerceIn(0, 255)
                g = g.coerceIn(0, 255)
                b = b.coerceIn(0, 255)

                outPixels[outIndex++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        return Bitmap.createBitmap(outPixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
