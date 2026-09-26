package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MediaType {
    PHOTO,
    VIDEO
}

@Entity(tableName = "media_items")
data class MediaItemEntity(
    @PrimaryKey
    val id: String, // UUID
    val mediaType: MediaType,
    val pngPath: String? = null,
    val dngPath: String? = null,
    val videoPath: String? = null,
    val thumbnailPath: String,
    val timestamp: Long,
    val cameraId: String,
    val cameraLabel: String,
    val width: Int,
    val height: Int,
    val durationMs: Long? = null,
    val fileSizeBytes: Long,
    val dngSizeBytes: Long? = null,
    val orientationDegrees: Int = 0,
    val iso: Int? = null,
    val exposureTime: String? = null,
    val focalLength: Float? = null,
    val aperture: Float? = null,
    val hasRaw: Boolean = false,
    val isFinalized: Boolean = true
)
