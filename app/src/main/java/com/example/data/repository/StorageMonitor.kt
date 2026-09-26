package com.example.data.repository

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class StorageInfo(
    val availableBytes: Long,
    val totalBytes: Long,
    val estimatedPhotosRemaining: Int,
    val estimatedVideoMinutesRemaining: Int,
    val photosUsageBytes: Long,
    val videosUsageBytes: Long,
    val rawUsageBytes: Long,
    val isLowStorage: Boolean
)

class StorageMonitor(private val context: Context) {

    // Thresholds
    private val lowStorageThresholdBytes = 250L * 1024L * 1024L // 250 MB
    private val avgPngSizeBytes = 12L * 1024L * 1024L // ~12 MB average lossless PNG
    private val avgVideoBitrateBytesPerMin = 150L * 1024L * 1024L // ~150 MB/min for high quality 1080p/4K

    suspend fun checkStorage(): StorageInfo = withContext(Dispatchers.IO) {
        val privateDir = context.filesDir
        val stat = StatFs(privateDir.path)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        val totalBytes = stat.blockCountLong * stat.blockSizeLong

        val photosDir = File(privateDir, "photos")
        val videosDir = File(privateDir, "videos")
        val rawDir = File(privateDir, "raw")

        val photosUsage = calculateDirSize(photosDir)
        val videosUsage = calculateDirSize(videosDir)
        val rawUsage = calculateDirSize(rawDir)

        val estimatedPhotos = if (availableBytes > 0) (availableBytes / avgPngSizeBytes).toInt() else 0
        val estimatedVideoMins = if (availableBytes > 0) (availableBytes / avgVideoBitrateBytesPerMin).toInt() else 0
        val isLow = availableBytes < lowStorageThresholdBytes

        StorageInfo(
            availableBytes = availableBytes,
            totalBytes = totalBytes,
            estimatedPhotosRemaining = estimatedPhotos,
            estimatedVideoMinutesRemaining = estimatedVideoMins,
            photosUsageBytes = photosUsage,
            videosUsageBytes = videosUsage,
            rawUsageBytes = rawUsage,
            isLowStorage = isLow
        )
    }

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists() || !dir.isDirectory) return 0L
        var total = 0L
        dir.listFiles()?.forEach { file ->
            if (file.isFile) total += file.length()
            else if (file.isDirectory) total += calculateDirSize(file)
        }
        return total
    }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
            val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
            return String.format("%.1f %s", value, units[digitGroups])
        }
    }
}
