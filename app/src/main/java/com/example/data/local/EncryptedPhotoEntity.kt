package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity representing an encrypted media artifact stored within the vault.
 * All file records point to encrypted ciphertext payloads saved in the app's internal sandbox.
 */
@Entity(tableName = "encrypted_photos")
data class EncryptedPhotoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val originalFileName: String,
    val encryptedFileName: String,
    val fileSizeBytes: Long,
    val mimeType: String,
    val importedTimestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val notes: String = "",
    val width: Int = 0,
    val height: Int = 0
)
