package com.example.security

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * High-performance mobile security utility for encrypting and decrypting data
 * using the Android Keystore System and AES-256 in GCM (Galois/Counter Mode).
 *
 * Security Guarantees:
 * 1. Master AES Key is securely generated and hardware-backed inside the Android KeyStore (TEE / Secure Element).
 * 2. Authenticated Encryption with Associated Data (AEAD) ensures both confidentiality and data integrity.
 * 3. A unique 12-byte IV (Initialization Vector) is generated for each encryption operation and prefixed to the ciphertext.
 * 4. Plaintext images are never cached to disk and reside purely in memory (Bitmap) during active viewing.
 */
object CryptoManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "PhotoVault_Master_AES256_GCM_Key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }
    }

    /**
     * Retrieves the existing AES SecretKey from the Android Keystore,
     * or generates a new hardware-backed 256-bit AES key if not already present.
     */
    @Synchronized
    fun getOrCreateSecretKey(): SecretKey {
        val existingKey = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existingKey != null) {
            return existingKey.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypts a raw ByteArray using AES-256-GCM.
     * The returned ByteArray is formatted as: [ 12-byte IV ] + [ CipherText + 16-byte Auth Tag ]
     */
    fun encrypt(rawBytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv // 12-byte IV generated automatically by Keystore provider
        val ciphertext = cipher.doFinal(rawBytes)

        val output = ByteArrayOutputStream(iv.size + ciphertext.size)
        output.write(iv)
        output.write(ciphertext)
        return output.toByteArray()
    }

    /**
     * Decrypts an encrypted ByteArray formatted as: [ 12-byte IV ] + [ CipherText + Auth Tag ]
     * Returns the original plaintext ByteArray.
     * Throws an exception if authentication tag verification fails (tamper detection).
     */
    fun decrypt(encryptedBytes: ByteArray): ByteArray {
        require(encryptedBytes.size > GCM_IV_LENGTH_BYTES) { "Ciphertext too short to contain IV and data" }

        val iv = encryptedBytes.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val cipherTextWithTag = encryptedBytes.copyOfRange(GCM_IV_LENGTH_BYTES, encryptedBytes.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)

        return cipher.doFinal(cipherTextWithTag)
    }

    /**
     * Streams raw data from [inputStream], encrypts it with AES-GCM, and writes
     * the [IV + ciphertext] directly to [outputStream].
     */
    fun encryptStream(inputStream: InputStream, outputStream: OutputStream) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv

        // Write the 12-byte IV at the beginning of the stream
        outputStream.write(iv)

        CipherOutputStream(outputStream, cipher).use { cipherOut ->
            inputStream.copyTo(cipherOut)
        }
    }

    /**
     * Decrypts an encrypted file directly into an in-memory [Bitmap].
     * Plaintext bytes are NEVER written to the disk or cache directory.
     */
    fun decryptFileToBitmap(encryptedFile: File): Bitmap? {
        if (!encryptedFile.exists() || encryptedFile.length() <= GCM_IV_LENGTH_BYTES) {
            return null
        }

        return try {
            FileInputStream(encryptedFile).use { fileIn ->
                val iv = ByteArray(GCM_IV_LENGTH_BYTES)
                val readBytes = fileIn.read(iv)
                if (readBytes != GCM_IV_LENGTH_BYTES) {
                    return null
                }

                val cipher = Cipher.getInstance(TRANSFORMATION)
                val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)

                CipherInputStream(fileIn, cipher).use { cipherIn ->
                    BitmapFactory.decodeStream(cipherIn)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Decrypts an encrypted file completely into memory as a ByteArray.
     */
    fun decryptFileToBytes(encryptedFile: File): ByteArray? {
        if (!encryptedFile.exists() || encryptedFile.length() <= GCM_IV_LENGTH_BYTES) {
            return null
        }

        return try {
            FileInputStream(encryptedFile).use { fileIn ->
                val iv = ByteArray(GCM_IV_LENGTH_BYTES)
                val readBytes = fileIn.read(iv)
                if (readBytes != GCM_IV_LENGTH_BYTES) {
                    return null
                }

                val cipher = Cipher.getInstance(TRANSFORMATION)
                val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)

                CipherInputStream(fileIn, cipher).use { cipherIn ->
                    cipherIn.readBytes()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
