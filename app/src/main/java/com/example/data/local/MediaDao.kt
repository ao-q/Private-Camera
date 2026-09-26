package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_items WHERE isFinalized = 1 ORDER BY timestamp DESC")
    fun getAllFinalizedMedia(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items ORDER BY timestamp DESC")
    suspend fun getAllMediaList(): List<MediaItemEntity>

    @Query("SELECT * FROM media_items WHERE id = :id LIMIT 1")
    suspend fun getMediaById(id: String): MediaItemEntity?

    @Query("SELECT * FROM media_items WHERE isFinalized = 1 ORDER BY timestamp DESC LIMIT 1")
    fun getLatestMedia(): Flow<MediaItemEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(item: MediaItemEntity)

    @Update
    suspend fun updateMedia(item: MediaItemEntity)

    @Delete
    suspend fun deleteMedia(item: MediaItemEntity)

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun deleteMediaById(id: String)

    @Query("DELETE FROM media_items WHERE id IN (:ids)")
    suspend fun deleteMediaByIds(ids: List<String>)

    @Query("SELECT * FROM media_items WHERE isFinalized = 0")
    suspend fun getUnfinalizedMedia(): List<MediaItemEntity>
}
