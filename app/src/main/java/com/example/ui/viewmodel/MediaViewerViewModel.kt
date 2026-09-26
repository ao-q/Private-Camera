package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.PrivateCameraApp
import com.example.data.local.MediaItemEntity
import com.example.data.local.MediaType
import com.example.data.repository.ShareOption
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MediaViewerUiState(
    val mediaItem: MediaItemEntity? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Int = 0,
    val durationMs: Int = 0,
    val isMuted: Boolean = false,
    val showDetailsModal: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val showShareDialog: Boolean = false,
    val isDeleted: Boolean = false,
    val exportSuccessMessage: String? = null
)

class MediaViewerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PrivateCameraApp
    private val repository = app.mediaRepository

    private val _uiState = MutableStateFlow(MediaViewerUiState())
    val uiState: StateFlow<MediaViewerUiState> = _uiState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    fun loadMedia(id: String) {
        viewModelScope.launch {
            val item = repository.getMediaById(id)
            _uiState.update { it.copy(mediaItem = item) }
        }
    }

    fun initializeVideoPlayer(context: Context, videoPath: String) {
        releasePlayer()
        try {
            val player = MediaPlayer().apply {
                setDataSource(videoPath)
                setOnPreparedListener { mp ->
                    _uiState.update {
                        it.copy(
                            durationMs = mp.duration,
                            currentPositionMs = 0
                        )
                    }
                }
                setOnCompletionListener {
                    _uiState.update { it.copy(isPlaying = false, currentPositionMs = it.durationMs) }
                    progressJob?.cancel()
                }
                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            _uiState.update { it.copy(isPlaying = false) }
            progressJob?.cancel()
        } else {
            player.start()
            _uiState.update { it.copy(isPlaying = true) }
            startProgressTracker()
        }
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.seekTo(positionMs)
        _uiState.update { it.copy(currentPositionMs = positionMs) }
    }

    fun toggleMute() {
        val player = mediaPlayer ?: return
        val currentlyMuted = _uiState.value.isMuted
        val nextMuted = !currentlyMuted
        if (nextMuted) {
            player.setVolume(0f, 0f)
        } else {
            player.setVolume(1f, 1f)
        }
        _uiState.update { it.copy(isMuted = nextMuted) }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        _uiState.update { it.copy(currentPositionMs = mp.currentPosition) }
                    }
                }
                delay(200)
            }
        }
    }

    fun openDetails() = _uiState.update { it.copy(showDetailsModal = true) }
    fun closeDetails() = _uiState.update { it.copy(showDetailsModal = false) }

    fun openDeleteDialog() = _uiState.update { it.copy(showDeleteDialog = true) }
    fun closeDeleteDialog() = _uiState.update { it.copy(showDeleteDialog = false) }

    fun openShareDialog() = _uiState.update { it.copy(showShareDialog = true) }
    fun closeShareDialog() = _uiState.update { it.copy(showShareDialog = false) }

    fun confirmDelete() {
        val item = _uiState.value.mediaItem ?: return
        viewModelScope.launch {
            releasePlayer()
            repository.deleteMediaItem(item)
            _uiState.update { it.copy(isDeleted = true, showDeleteDialog = false) }
        }
    }

    fun createShareIntent(option: ShareOption): Intent? {
        val item = _uiState.value.mediaItem ?: return null
        return repository.createShareIntent(item, option)
    }

    fun exportToDestination(sourcePath: String, destUri: Uri) {
        viewModelScope.launch {
            val success = repository.exportFileToUri(sourcePath, destUri)
            _uiState.update {
                it.copy(
                    exportSuccessMessage = if (success) "Exported successfully to device storage" else "Export failed"
                )
            }
        }
    }

    fun clearExportMessage() {
        _uiState.update { it.copy(exportSuccessMessage = null) }
    }

    fun releasePlayer() {
        progressJob?.cancel()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        _uiState.update { it.copy(isPlaying = false, currentPositionMs = 0) }
    }

    override fun onCleared() {
        releasePlayer()
        super.onCleared()
    }
}
