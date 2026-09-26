package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.PrivateCameraApp
import com.example.data.local.MediaItemEntity
import com.example.data.repository.ShareOption
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class GalleryUiState(
    val mediaItems: List<MediaItemEntity> = emptyList(),
    val isSelectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val isDeleting: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val shareIntent: Intent? = null
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PrivateCameraApp
    private val repository = app.mediaRepository

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.allMedia.collect { items ->
                _uiState.update { current ->
                    // Clean up any selected ids that no longer exist
                    val validSelected = current.selectedIds.filter { id -> items.any { it.id == id } }.toSet()
                    current.copy(
                        mediaItems = items,
                        selectedIds = validSelected,
                        isSelectionMode = current.isSelectionMode && validSelected.isNotEmpty()
                    )
                }
            }
        }
    }

    fun toggleSelectionMode() {
        _uiState.update {
            val newMode = !it.isSelectionMode
            it.copy(
                isSelectionMode = newMode,
                selectedIds = if (!newMode) emptySet() else it.selectedIds
            )
        }
    }

    fun toggleItemSelection(id: String) {
        _uiState.update { current ->
            val newSelection = if (current.selectedIds.contains(id)) {
                current.selectedIds - id
            } else {
                current.selectedIds + id
            }
            current.copy(
                selectedIds = newSelection,
                isSelectionMode = newSelection.isNotEmpty()
            )
        }
    }

    fun selectAll() {
        val allIds = _uiState.value.mediaItems.map { it.id }.toSet()
        _uiState.update { it.copy(selectedIds = allIds, isSelectionMode = true) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet(), isSelectionMode = false) }
    }

    fun promptDeleteSelected() {
        if (_uiState.value.selectedIds.isNotEmpty()) {
            _uiState.update { it.copy(showDeleteConfirmation = true) }
        }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(showDeleteConfirmation = false) }
    }

    fun confirmDeleteSelected() {
        val selectedIds = _uiState.value.selectedIds
        val itemsToDelete = _uiState.value.mediaItems.filter { selectedIds.contains(it.id) }
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true, showDeleteConfirmation = false) }
            repository.deleteMediaItems(itemsToDelete)
            _uiState.update { it.copy(isDeleting = false, selectedIds = emptySet(), isSelectionMode = false) }
        }
    }

    fun createShareIntentForSelected(context: Context): Intent? {
        val selectedIds = _uiState.value.selectedIds
        val selectedItems = _uiState.value.mediaItems.filter { selectedIds.contains(it.id) }
        if (selectedItems.isEmpty()) return null

        if (selectedItems.size == 1) {
            return repository.createShareIntent(selectedItems.first(), ShareOption.PNG_ONLY)
        }

        // Multiple files sharing
        val uris = ArrayList<Uri>()
        for (item in selectedItems) {
            item.pngPath?.let { uris.add(repository.getShareUriForFile(it)) }
            item.videoPath?.let { uris.add(repository.getShareUriForFile(it)) }
        }

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(intent, "Share ${uris.size} Items")
    }
}
