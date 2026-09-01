package com.example.ui.screens.gallery

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.EncryptedPhotoEntity
import com.example.data.repository.ImportResult
import com.example.data.repository.VaultRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class VaultFilter {
    ALL,
    FAVORITES
}

data class VaultUiState(
    val photos: List<EncryptedPhotoEntity> = emptyList(),
    val filter: VaultFilter = VaultFilter.ALL,
    val searchQuery: String = "",
    val isImporting: Boolean = false,
    val importProgress: Float = 0f,
    val selectedPhotoForViewing: EncryptedPhotoEntity? = null,
    val infoMessage: String? = null,
    val errorMessage: String? = null,
    val recentlyImportedUris: List<Uri> = emptyList(),
    val showDeleteOriginalsDialog: Boolean = false
)

private data class VaultControlState(
    val filter: VaultFilter = VaultFilter.ALL,
    val searchQuery: String = "",
    val isImporting: Boolean = false,
    val importProgress: Float = 0f,
    val selectedPhotoForViewing: EncryptedPhotoEntity? = null,
    val infoMessage: String? = null,
    val errorMessage: String? = null,
    val recentlyImportedUris: List<Uri> = emptyList(),
    val showDeleteOriginalsDialog: Boolean = false
)

class VaultViewModel(
    private val repository: VaultRepository
) : ViewModel() {

    private val _controlState = MutableStateFlow(VaultControlState())

    val totalPhotoCount: StateFlow<Int> = repository.photoCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val totalVaultSize: StateFlow<Long?> = repository.totalVaultSize.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0L
    )

    val uiState: StateFlow<VaultUiState> = combine(
        repository.allPhotos,
        _controlState
    ) { allPhotos, control ->
        val filteredByTab = when (control.filter) {
            VaultFilter.ALL -> allPhotos
            VaultFilter.FAVORITES -> allPhotos.filter { it.isFavorite }
        }

        val finalPhotos = if (control.searchQuery.isBlank()) {
            filteredByTab
        } else {
            filteredByTab.filter {
                it.originalFileName.contains(control.searchQuery, ignoreCase = true) ||
                it.notes.contains(control.searchQuery, ignoreCase = true)
            }
        }

        VaultUiState(
            photos = finalPhotos,
            filter = control.filter,
            searchQuery = control.searchQuery,
            isImporting = control.isImporting,
            importProgress = control.importProgress,
            selectedPhotoForViewing = control.selectedPhotoForViewing,
            infoMessage = control.infoMessage,
            errorMessage = control.errorMessage,
            recentlyImportedUris = control.recentlyImportedUris,
            showDeleteOriginalsDialog = control.showDeleteOriginalsDialog
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VaultUiState()
    )

    fun setFilter(filter: VaultFilter) {
        _controlState.update { it.copy(filter = filter) }
    }

    fun setSearchQuery(query: String) {
        _controlState.update { it.copy(searchQuery = query) }
    }

    fun selectPhoto(photo: EncryptedPhotoEntity?) {
        _controlState.update { it.copy(selectedPhotoForViewing = photo) }
    }

    fun clearMessages() {
        _controlState.update { it.copy(infoMessage = null, errorMessage = null) }
    }

    fun dismissDeleteOriginalsDialog() {
        _controlState.update {
            it.copy(
                showDeleteOriginalsDialog = false,
                recentlyImportedUris = emptyList()
            )
        }
    }

    /**
     * Imports multiple photos selected via Photo Picker, encrypts them into internal storage,
     * and triggers a confirmation dialog to delete original unprotected files from gallery.
     */
    fun importPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return

        viewModelScope.launch {
            _controlState.update { it.copy(isImporting = true, importProgress = 0f) }
            val successfulUris = mutableListOf<Uri>()

            uris.forEachIndexed { index, uri ->
                when (val result = repository.importAndEncryptPhoto(uri)) {
                    is ImportResult.Success -> {
                        successfulUris.add(result.originalUri)
                    }
                    is ImportResult.Error -> {
                        _controlState.update { it.copy(errorMessage = "Failed: ${result.message}") }
                    }
                }
                val progress = (index + 1).toFloat() / uris.size
                _controlState.update { it.copy(importProgress = progress) }
            }

            _controlState.update {
                it.copy(
                    isImporting = false,
                    infoMessage = if (successfulUris.isNotEmpty()) {
                        "Successfully encrypted ${successfulUris.size} photo(s) with AES-256"
                    } else it.infoMessage,
                    recentlyImportedUris = successfulUris,
                    showDeleteOriginalsDialog = successfulUris.isNotEmpty()
                )
            }
        }
    }

    suspend fun decryptThumbnail(encryptedFileName: String, targetSize: Int): Bitmap? {
        return repository.getDecryptedBitmap(encryptedFileName, targetSize)
    }

    suspend fun decryptFullImage(encryptedFileName: String): Bitmap? {
        return repository.getDecryptedBitmap(encryptedFileName, null)
    }

    fun toggleFavorite(photo: EncryptedPhotoEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(photo)
            _controlState.update { state ->
                if (state.selectedPhotoForViewing?.id == photo.id) {
                    state.copy(selectedPhotoForViewing = photo.copy(isFavorite = !photo.isFavorite))
                } else state
            }
        }
    }

    fun updateNotes(photoId: Long, notes: String) {
        viewModelScope.launch {
            repository.updateNotes(photoId, notes)
            _controlState.update { state ->
                if (state.selectedPhotoForViewing?.id == photoId) {
                    state.copy(selectedPhotoForViewing = state.selectedPhotoForViewing?.copy(notes = notes))
                } else state
            }
        }
    }

    fun exportPhotoToGallery(photo: EncryptedPhotoEntity) {
        viewModelScope.launch {
            val success = repository.exportPhotoToGallery(photo)
            _controlState.update {
                if (success) {
                    it.copy(infoMessage = "Photo restored to public gallery (Pictures/PhotoVault)")
                } else {
                    it.copy(errorMessage = "Failed to export photo to gallery")
                }
            }
        }
    }

    fun deletePhoto(photo: EncryptedPhotoEntity) {
        viewModelScope.launch {
            val success = repository.deletePhoto(photo)
            _controlState.update { state ->
                val newSelected = if (state.selectedPhotoForViewing?.id == photo.id) null else state.selectedPhotoForViewing
                if (success) {
                    state.copy(
                        selectedPhotoForViewing = newSelected,
                        infoMessage = "Permanently deleted encrypted file"
                    )
                } else {
                    state.copy(errorMessage = "Failed to delete file")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.clearMemoryCache()
    }

    class Factory(private val repository: VaultRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(VaultViewModel::class.java)) {
                return VaultViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
