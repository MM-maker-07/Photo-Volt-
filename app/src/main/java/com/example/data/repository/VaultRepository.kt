package com.example.data.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.LruCache
import com.example.data.local.EncryptedPhotoEntity
import com.example.data.local.PhotoDao
import com.example.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Production-ready repository orchestrating AES-256-GCM encryption,
 * internal private sandboxed file management, and Room database persistence.
 */
class VaultRepository(
    private val context: Context,
    private val photoDao: PhotoDao
) {
    private val vaultDirectory: File by lazy {
        File(context.filesDir, "vault_photos").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    // In-memory memory-only LRU cache for decrypted Bitmaps (prevents disk caching leaks & optimizes scroll FPS)
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8
    private val bitmapMemoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    val allPhotos: Flow<List<EncryptedPhotoEntity>> = photoDao.getAllPhotos()
    val favoritePhotos: Flow<List<EncryptedPhotoEntity>> = photoDao.getFavoritePhotos()
    val photoCount: Flow<Int> = photoDao.getPhotoCount()
    val totalVaultSize: Flow<Long?> = photoDao.getTotalVaultSizeBytes()

    /**
     * Imports and encrypts an image from an external Uri (such as Photo Picker or MediaStore).
     * The plaintext data is read into memory, encrypted with AES-256-GCM, and written
     * directly to internal storage as a .vault file.
     */
    suspend fun importAndEncryptPhoto(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val originalFileName = queryFileName(uri) ?: "photo_${System.currentTimeMillis()}.jpg"
            val mimeType = contentResolver.getType(uri) ?: "image/jpeg"

            // Read original raw image bytes
            val rawBytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext ImportResult.Error("Could not read image content")

            // Decode image dimensions (in memory, without saving)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, options)
            val width = options.outWidth
            val height = options.outHeight

            // Encrypt using AES-256-GCM via Keystore
            val encryptedBytes = CryptoManager.encrypt(rawBytes)

            // Save encrypted file to internal storage
            val encryptedFileName = "${UUID.randomUUID()}.vault"
            val encryptedFile = File(vaultDirectory, encryptedFileName)
            FileOutputStream(encryptedFile).use { it.write(encryptedBytes) }

            // Store metadata in Room
            val entity = EncryptedPhotoEntity(
                originalFileName = originalFileName,
                encryptedFileName = encryptedFileName,
                fileSizeBytes = encryptedBytes.size.toLong(),
                mimeType = mimeType,
                importedTimestamp = System.currentTimeMillis(),
                isFavorite = false,
                notes = "",
                width = width,
                height = height
            )
            val insertedId = photoDao.insertPhoto(entity)

            ImportResult.Success(
                photoId = insertedId,
                originalUri = uri,
                originalFileName = originalFileName
            )
        } catch (e: Exception) {
            e.printStackTrace()
            ImportResult.Error(e.localizedMessage ?: "Failed to encrypt and store photo")
        }
    }

    /**
     * Dynamically decrypts a photo in-memory into a [Bitmap].
     * Uses memory LRU cache for speed while guaranteeing no disk exposure.
     */
    suspend fun getDecryptedBitmap(encryptedFileName: String, targetSize: Int? = null): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = if (targetSize != null) "${encryptedFileName}_$targetSize" else encryptedFileName
        val cached = bitmapMemoryCache.get(cacheKey)
        if (cached != null && !cached.isRecycled) {
            return@withContext cached
        }

        val encryptedFile = File(vaultDirectory, encryptedFileName)
        if (!encryptedFile.exists()) return@withContext null

        val decryptedBytes = CryptoManager.decryptFileToBytes(encryptedFile) ?: return@withContext null

        val bitmap = if (targetSize != null) {
            decodeSampledBitmap(decryptedBytes, targetSize, targetSize)
        } else {
            BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size)
        }

        if (bitmap != null) {
            bitmapMemoryCache.put(cacheKey, bitmap)
        }
        bitmap
    }

    /**
     * Exports (unvaults) an encrypted image back to the public device gallery (DCIM/Pictures)
     * using the modern MediaStore API.
     */
    suspend fun exportPhotoToGallery(photo: EncryptedPhotoEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val encryptedFile = File(vaultDirectory, photo.encryptedFileName)
            val decryptedBytes = CryptoManager.decryptFileToBytes(encryptedFile) ?: return@withContext false

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "Restored_${photo.originalFileName}")
                put(MediaStore.Images.Media.MIME_TYPE, photo.mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/PhotoVault")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@withContext false

            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(decryptedBytes)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Permanently purges an encrypted photo from both internal disk and database.
     */
    suspend fun deletePhoto(photo: EncryptedPhotoEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(vaultDirectory, photo.encryptedFileName)
            if (file.exists()) {
                file.delete()
            }
            bitmapMemoryCache.remove(photo.encryptedFileName)
            photoDao.deletePhotoById(photo.id)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun toggleFavorite(photo: EncryptedPhotoEntity) = withContext(Dispatchers.IO) {
        photoDao.updateFavorite(photo.id, !photo.isFavorite)
    }

    suspend fun updateNotes(photoId: Long, notes: String) = withContext(Dispatchers.IO) {
        photoDao.updateNotes(photoId, notes)
    }

    fun clearMemoryCache() {
        bitmapMemoryCache.evictAll()
    }

    private fun queryFileName(uri: Uri): String? {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) return cursor.getString(index)
                }
            }
        }
        return uri.path?.substringAfterLast('/')
    }

    private fun decodeSampledBitmap(data: ByteArray, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(data, 0, data.size, options)

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeByteArray(data, 0, data.size, options)
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}

sealed class ImportResult {
    data class Success(val photoId: Long, val originalUri: Uri, val originalFileName: String) : ImportResult()
    data class Error(val message: String) : ImportResult()
}
