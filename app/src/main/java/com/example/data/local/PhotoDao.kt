package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {

    @Query("SELECT * FROM encrypted_photos ORDER BY importedTimestamp DESC")
    fun getAllPhotos(): Flow<List<EncryptedPhotoEntity>>

    @Query("SELECT * FROM encrypted_photos WHERE isFavorite = 1 ORDER BY importedTimestamp DESC")
    fun getFavoritePhotos(): Flow<List<EncryptedPhotoEntity>>

    @Query("SELECT * FROM encrypted_photos WHERE id = :id LIMIT 1")
    suspend fun getPhotoById(id: Long): EncryptedPhotoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: EncryptedPhotoEntity): Long

    @Update
    suspend fun updatePhoto(photo: EncryptedPhotoEntity)

    @Query("DELETE FROM encrypted_photos WHERE id = :id")
    suspend fun deletePhotoById(id: Long)

    @Query("UPDATE encrypted_photos SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE encrypted_photos SET notes = :notes WHERE id = :id")
    suspend fun updateNotes(id: Long, notes: String)

    @Query("SELECT COUNT(*) FROM encrypted_photos")
    fun getPhotoCount(): Flow<Int>

    @Query("SELECT SUM(fileSizeBytes) FROM encrypted_photos")
    fun getTotalVaultSizeBytes(): Flow<Long?>
}
