package com.example.security

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Manages secure user authentication state, salted PIN hash storage,
 * and rate-limiting brute force lockout counters.
 */
class SecurityPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "photo_vault_security_prefs"
        private const val KEY_PIN_HASH = "key_pin_hash"
        private const val KEY_PIN_SALT = "key_pin_salt"
        private const val KEY_BIOMETRICS_ENABLED = "key_biometrics_enabled"
        private const val KEY_FAILED_ATTEMPTS = "key_failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "key_lockout_until"

        private const val MAX_FAILED_ATTEMPTS = 5
        private const val LOCKOUT_DURATION_MS = 30_000L // 30 seconds
    }

    /**
     * Checks if a user master PIN has already been set up.
     */
    fun isPinSet(): Boolean {
        return prefs.getString(KEY_PIN_HASH, null) != null
    }

    /**
     * Registers a new 4-digit PIN.
     * Computes a SHA-256 hash combined with a cryptographically secure random salt.
     */
    fun setPin(pin: String): Boolean {
        if (pin.length != 4 || !pin.all { it.isDigit() }) return false

        val salt = generateSalt()
        val hash = hashPinWithSalt(pin, salt)

        prefs.edit()
            .putString(KEY_PIN_SALT, salt)
            .putString(KEY_PIN_HASH, hash)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .apply()
        return true
    }

    /**
     * Verifies the provided 4-digit PIN against the stored salted hash.
     * Enforces rate limiting against brute force attempts.
     */
    fun verifyPin(pin: String): PinVerificationResult {
        val now = System.currentTimeMillis()
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)

        if (now < lockoutUntil) {
            val remainingSeconds = ((lockoutUntil - now) / 1000).toInt() + 1
            return PinVerificationResult.LockedOut(remainingSeconds)
        }

        val storedHash = prefs.getString(KEY_PIN_HASH, null)
        val storedSalt = prefs.getString(KEY_PIN_SALT, null)

        if (storedHash == null || storedSalt == null) {
            return PinVerificationResult.PinNotSet
        }

        val calculatedHash = hashPinWithSalt(pin, storedSalt)
        return if (calculatedHash == storedHash) {
            // Reset failed counter on success
            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL, 0L)
                .apply()
            PinVerificationResult.Success
        } else {
            val failedAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                val newLockoutUntil = now + LOCKOUT_DURATION_MS
                prefs.edit()
                    .putInt(KEY_FAILED_ATTEMPTS, 0)
                    .putLong(KEY_LOCKOUT_UNTIL, newLockoutUntil)
                    .apply()
                PinVerificationResult.LockedOut((LOCKOUT_DURATION_MS / 1000).toInt())
            } else {
                prefs.edit().putInt(KEY_FAILED_ATTEMPTS, failedAttempts).apply()
                PinVerificationResult.Incorrect(remainingAttempts = MAX_FAILED_ATTEMPTS - failedAttempts)
            }
        }
    }

    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRICS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRICS_ENABLED, value).apply()

    fun resetSecurityData() {
        prefs.edit().clear().apply()
    }

    private fun generateSalt(): String {
        val random = SecureRandom()
        val saltBytes = ByteArray(16)
        random.nextBytes(saltBytes)
        return saltBytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashPinWithSalt(pin: String, saltHex: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = "$saltHex:$pin:PhotoVaultSecurePepper"
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}

sealed class PinVerificationResult {
    object Success : PinVerificationResult()
    object PinNotSet : PinVerificationResult()
    data class Incorrect(val remainingAttempts: Int) : PinVerificationResult()
    data class LockedOut(val remainingSeconds: Int) : PinVerificationResult()
}
