package com.example.camera.capture

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.DngCreator
import android.media.Image
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object RawDngWriter {

    /**
     * Writes an Image in ImageFormat.RAW_SENSOR directly to an Adobe DNG file using Camera2 DngCreator.
     */
    suspend fun writeRawDng(
        image: Image,
        characteristics: CameraCharacteristics,
        captureResult: CaptureResult,
        destinationFile: File,
        orientation: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val dngCreator = DngCreator(characteristics, captureResult)
            dngCreator.setOrientation(orientation)
            FileOutputStream(destinationFile).use { fos ->
                dngCreator.writeImage(fos, image)
                fos.flush()
            }
            dngCreator.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
