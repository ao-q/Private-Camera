package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.local.AppDatabase
import com.example.data.local.MediaDao
import com.example.data.local.MediaItemEntity
import com.example.data.local.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class MediaRepository(private val context: Context) {
    private val mediaDao: MediaDao = AppDatabase.getDatabase(context).mediaDao()

    val allMedia: Flow<List<MediaItemEntity>> = mediaDao.getAllFinalizedMedia()
    val latestMedia: Flow<MediaItemEntity?> = mediaDao.getLatestMedia()

    val photosDir: File = File(context.filesDir, "photos").apply { if (!exists()) mkdirs() }
    val videosDir: File = File(context.filesDir, "videos").apply { if (!exists()) mkdirs() }
    val rawDir: File = File(context.filesDir, "raw").apply { if (!exists()) mkdirs() }
    val thumbnailsDir: File = File(context.filesDir, "thumbnails").apply { if (!exists()) mkdirs() }
    val tempDir: File = File(context.filesDir, "temp").apply { if (!exists()) mkdirs() }

    init {
        // Ensure directories exist
        photosDir.mkdirs()
        videosDir.mkdirs()
        rawDir.mkdirs()
        thumbnailsDir.mkdirs()
        tempDir.mkdirs()
    }

    suspend fun getMediaById(id: String): MediaItemEntity? = withContext(Dispatchers.IO) {
        mediaDao.getMediaById(id)
    }

    fun createTempFile(prefix: String, suffix: String): File {
        return File(tempDir, "${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}$suffix")
    }

    suspend fun finalizePhotoCapture(
        tempPngFile: File,
        tempDngFile: File?,
        cameraId: String,
        cameraLabel: String,
        width: Int,
        height: Int,
        orientationDegrees: Int,
        iso: Int?,
        exposureTime: String?,
        focalLength: Float?,
        aperture: Float?
    ): MediaItemEntity = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        val finalPngFile = File(photosDir, "IMG_${timestamp}_$id.png")
        if (tempPngFile.exists()) {
            tempPngFile.renameTo(finalPngFile)
        }

        var finalDngFile: File? = null
        var dngSize: Long? = null
        if (tempDngFile != null && tempDngFile.exists()) {
            finalDngFile = File(rawDir, "RAW_${timestamp}_$id.dng")
            tempDngFile.renameTo(finalDngFile)
            dngSize = finalDngFile.length()
        }

        // Generate thumbnail
        val thumbFile = File(thumbnailsDir, "THUMB_${timestamp}_$id.jpg")
        generatePhotoThumbnail(finalPngFile, thumbFile)

        val entity = MediaItemEntity(
            id = id,
            mediaType = MediaType.PHOTO,
            pngPath = finalPngFile.absolutePath,
            dngPath = finalDngFile?.absolutePath,
            videoPath = null,
            thumbnailPath = thumbFile.absolutePath,
            timestamp = timestamp,
            cameraId = cameraId,
            cameraLabel = cameraLabel,
            width = width,
            height = height,
            durationMs = null,
            fileSizeBytes = finalPngFile.length(),
            dngSizeBytes = dngSize,
            orientationDegrees = orientationDegrees,
            iso = iso,
            exposureTime = exposureTime,
            focalLength = focalLength,
            aperture = aperture,
            hasRaw = finalDngFile != null,
            isFinalized = true
        )

        mediaDao.insertMedia(entity)
        entity
    }

    suspend fun finalizeVideoCapture(
        tempVideoFile: File,
        cameraId: String,
        cameraLabel: String,
        width: Int,
        height: Int,
        orientationDegrees: Int,
        durationMs: Long
    ): MediaItemEntity? = withContext(Dispatchers.IO) {
        if (!tempVideoFile.exists() || tempVideoFile.length() <= 0) {
            tempVideoFile.delete()
            return@withContext null
        }

        val id = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val finalVideoFile = File(videosDir, "VID_${timestamp}_$id.mp4")
        tempVideoFile.renameTo(finalVideoFile)

        // Generate video thumbnail
        val thumbFile = File(thumbnailsDir, "THUMB_VID_${timestamp}_$id.jpg")
        generateVideoThumbnail(finalVideoFile, thumbFile)

        // Read actual video duration & resolution if retriever can extract
        var finalWidth = width
        var finalHeight = height
        var finalDuration = durationMs

        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(finalVideoFile.absolutePath)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (durStr != null) {
                finalDuration = durStr.toLongOrNull() ?: durationMs
            }
            val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            if (wStr != null && hStr != null) {
                finalWidth = wStr.toIntOrNull() ?: width
                finalHeight = hStr.toIntOrNull() ?: height
            }
            retriever.release()
        } catch (_: Exception) {
            // fallback to passed parameters
        }

        val entity = MediaItemEntity(
            id = id,
            mediaType = MediaType.VIDEO,
            pngPath = null,
            dngPath = null,
            videoPath = finalVideoFile.absolutePath,
            thumbnailPath = thumbFile.absolutePath,
            timestamp = timestamp,
            cameraId = cameraId,
            cameraLabel = cameraLabel,
            width = finalWidth,
            height = finalHeight,
            durationMs = finalDuration,
            fileSizeBytes = finalVideoFile.length(),
            dngSizeBytes = null,
            orientationDegrees = orientationDegrees,
            hasRaw = false,
            isFinalized = true
        )

        mediaDao.insertMedia(entity)
        entity
    }

    private fun generatePhotoThumbnail(pngFile: File, thumbFile: File) {
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(pngFile.absolutePath, options)

            val reqWidth = 320
            val reqHeight = 320
            var inSampleSize = 1
            if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2
                }
            }

            options.inJustDecodeBounds = false
            options.inSampleSize = inSampleSize
            val bitmap = BitmapFactory.decodeFile(pngFile.absolutePath, options)
            if (bitmap != null) {
                FileOutputStream(thumbFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                bitmap.recycle()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun generateVideoThumbnail(videoFile: File, thumbFile: File) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(videoFile.absolutePath)
            val bitmap = retriever.getFrameAtTime(500000) // 0.5 sec frame
                ?: retriever.getFrameAtTime()
            retriever.release()

            if (bitmap != null) {
                val scaled = Bitmap.createScaledBitmap(
                    bitmap,
                    320,
                    (320 * (bitmap.height.toFloat() / bitmap.width.toFloat())).toInt().coerceAtLeast(180),
                    true
                )
                FileOutputStream(thumbFile).use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                if (scaled != bitmap) {
                    scaled.recycle()
                }
                bitmap.recycle()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun deleteMediaItem(item: MediaItemEntity) = withContext(Dispatchers.IO) {
        item.pngPath?.let { File(it).delete() }
        item.dngPath?.let { File(it).delete() }
        item.videoPath?.let { File(it).delete() }
        File(item.thumbnailPath).delete()
        mediaDao.deleteMedia(item)
    }

    suspend fun deleteMediaItems(items: List<MediaItemEntity>) = withContext(Dispatchers.IO) {
        items.forEach { item ->
            item.pngPath?.let { File(it).delete() }
            item.dngPath?.let { File(it).delete() }
            item.videoPath?.let { File(it).delete() }
            File(item.thumbnailPath).delete()
        }
        mediaDao.deleteMediaByIds(items.map { it.id })
    }

    suspend fun reconcileDatabaseWithFilesystem() = withContext(Dispatchers.IO) {
        try {
            // Clean temp directory of stale files
            tempDir.listFiles()?.forEach { file ->
                if (System.currentTimeMillis() - file.lastModified() > 30 * 60 * 1000) { // 30 mins old
                    file.delete()
                }
            }

            // Verify all items in DB exist on disk
            val allDbItems = mediaDao.getAllMediaList()
            for (item in allDbItems) {
                val primaryExists = when (item.mediaType) {
                    MediaType.PHOTO -> item.pngPath != null && File(item.pngPath).exists()
                    MediaType.VIDEO -> item.videoPath != null && File(item.videoPath).exists()
                }

                if (!primaryExists || !item.isFinalized) {
                    deleteMediaItem(item)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getShareUriForFile(filePath: String): Uri {
        val file = File(filePath)
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    fun createShareIntent(
        item: MediaItemEntity,
        shareOption: ShareOption = ShareOption.PNG_ONLY
    ): Intent {
        val intent = Intent(Intent.ACTION_SEND)
        when (item.mediaType) {
            MediaType.PHOTO -> {
                when (shareOption) {
                    ShareOption.PNG_ONLY -> {
                        item.pngPath?.let { path ->
                            val uri = getShareUriForFile(path)
                            intent.type = "image/png"
                            intent.putExtra(Intent.EXTRA_STREAM, uri)
                        }
                    }
                    ShareOption.RAW_DNG_ONLY -> {
                        item.dngPath?.let { path ->
                            val uri = getShareUriForFile(path)
                            intent.type = "image/x-adobe-dng"
                            intent.putExtra(Intent.EXTRA_STREAM, uri)
                        }
                    }
                    ShareOption.BOTH_PNG_AND_RAW -> {
                        val uris = ArrayList<Uri>()
                        item.pngPath?.let { uris.add(getShareUriForFile(it)) }
                        item.dngPath?.let { uris.add(getShareUriForFile(it)) }
                        intent.action = Intent.ACTION_SEND_MULTIPLE
                        intent.type = "image/*"
                        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    }
                }
            }
            MediaType.VIDEO -> {
                item.videoPath?.let { path ->
                    val uri = getShareUriForFile(path)
                    intent.type = "video/mp4"
                    intent.putExtra(Intent.EXTRA_STREAM, uri)
                }
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(intent, "Share Media (Private Camera)")
    }

    suspend fun exportFileToUri(sourceFilePath: String, destinationUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(sourceFilePath)
            if (!sourceFile.exists()) return@withContext false

            context.contentResolver.openOutputStream(destinationUri)?.use { output: OutputStream ->
                sourceFile.inputStream().use { input: InputStream ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

enum class ShareOption {
    PNG_ONLY,
    RAW_DNG_ONLY,
    BOTH_PNG_AND_RAW
}
