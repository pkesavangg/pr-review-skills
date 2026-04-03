package com.dmdbrands.gurus.weight.core.network

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.api.user.Token
import com.google.gson.Gson
import java.security.GeneralSecurityException

/**
 * Exception thrown when EncryptedSharedPreferences cannot be created or accessed.
 * This occurs when MasterKey creation fails (e.g., Samsung devices after OS updates,
 * backup restore to different hardware) or when the key is invalidated
 * (e.g., user changes device lock screen).
 */
class EncryptionUnavailableException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class SecureTokenStore(context: Context) : ISecureTokenStore {

    companion object {
        private const val TAG = "SecureTokenStore"
        private const val PREFS_FILE_NAME = "secure_tokens"
        private const val TOKEN_KEY_PREFIX = "token_"
        private const val MIGRATION_COMPLETED_KEY = "migration_completed"
        private const val MIGRATION_RETRY_COUNT_KEY = "migration_retry_count"
    }

    private val gson = Gson()
    private val sharedPreferences: SharedPreferences?

    /**
     * Whether encrypted storage is available.
     * If false, all token operations will throw [EncryptionUnavailableException].
     */
    val isAvailable: Boolean get() = sharedPreferences != null

    init {
        sharedPreferences = try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

            EncryptedSharedPreferences.create(
                PREFS_FILE_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: GeneralSecurityException) {
            AppLog.e(TAG, "Failed to create EncryptedSharedPreferences", e.toString())
            null
        } catch (e: Exception) {
            AppLog.e(TAG, "Unexpected error creating EncryptedSharedPreferences", e.toString())
            null
        }
    }

    @Throws(EncryptionUnavailableException::class)
    private fun requirePrefs(): SharedPreferences {
        return sharedPreferences
            ?: throw EncryptionUnavailableException("EncryptedSharedPreferences is unavailable — MasterKey creation failed or key was invalidated")
    }

    @Throws(EncryptionUnavailableException::class)
    override fun saveToken(accountId: String, token: Token) {
        val prefs = requirePrefs()
        try {
            val json = gson.toJson(token)
            prefs.edit()
                .putString("$TOKEN_KEY_PREFIX$accountId", json)
                .apply()
            AppLog.v(TAG, "Saved token for account: $accountId")
        } catch (e: GeneralSecurityException) {
            throw EncryptionUnavailableException("Failed to save token for account: $accountId", e)
        }
    }

    @Throws(EncryptionUnavailableException::class)
    override fun getToken(accountId: String): Token? {
        val prefs = requirePrefs()
        return try {
            val json = prefs.getString("$TOKEN_KEY_PREFIX$accountId", null)
            if (json != null) gson.fromJson(json, Token::class.java) else null
        } catch (e: GeneralSecurityException) {
            throw EncryptionUnavailableException("Failed to get token for account: $accountId", e)
        }
    }

    @Throws(EncryptionUnavailableException::class)
    override fun getAllTokens(): Map<String, Token> {
        val prefs = requirePrefs()
        val tokens = mutableMapOf<String, Token>()
        try {
            prefs.all.forEach { (key, value) ->
                if (key.startsWith(TOKEN_KEY_PREFIX) && value is String) {
                    val accountId = key.removePrefix(TOKEN_KEY_PREFIX)
                    val token = gson.fromJson(value, Token::class.java)
                    if (token != null) {
                        tokens[accountId] = token
                    }
                }
            }
        } catch (e: GeneralSecurityException) {
            throw EncryptionUnavailableException("Failed to get all tokens", e)
        }
        return tokens
    }

    @Throws(EncryptionUnavailableException::class)
    override fun removeToken(accountId: String) {
        val prefs = requirePrefs()
        try {
            prefs.edit()
                .remove("$TOKEN_KEY_PREFIX$accountId")
                .apply()
            AppLog.v(TAG, "Removed token for account: $accountId")
        } catch (e: GeneralSecurityException) {
            throw EncryptionUnavailableException("Failed to remove token for account: $accountId", e)
        }
    }

    @Throws(EncryptionUnavailableException::class)
    override fun clearAll() {
        val prefs = requirePrefs()
        try {
            val editor = prefs.edit()
            prefs.all.keys
                .filter { it.startsWith(TOKEN_KEY_PREFIX) }
                .forEach { editor.remove(it) }
            editor.apply()
            AppLog.v(TAG, "Cleared all tokens")
        } catch (e: GeneralSecurityException) {
            throw EncryptionUnavailableException("Failed to clear all tokens", e)
        }
    }

    @Throws(EncryptionUnavailableException::class)
    override fun hasTokens(): Boolean {
        val prefs = requirePrefs()
        return try {
            prefs.all.keys.any { it.startsWith(TOKEN_KEY_PREFIX) }
        } catch (e: GeneralSecurityException) {
            throw EncryptionUnavailableException("Failed to check for tokens", e)
        }
    }

    @Throws(EncryptionUnavailableException::class)
    fun isMigrationCompleted(): Boolean {
        val prefs = requirePrefs()
        return prefs.getBoolean(MIGRATION_COMPLETED_KEY, false)
    }

    @Throws(EncryptionUnavailableException::class)
    fun setMigrationCompleted() {
        val prefs = requirePrefs()
        prefs.edit().putBoolean(MIGRATION_COMPLETED_KEY, true).apply()
    }

    /**
     * Gets the number of migration retry attempts.
     * Returns 0 if encrypted storage is unavailable (to allow retry counting in plain prefs).
     */
    fun getMigrationRetryCount(): Int {
        val prefs = sharedPreferences ?: return 0
        return try {
            prefs.getInt(MIGRATION_RETRY_COUNT_KEY, 0)
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to read migration retry count, defaulting to 0: ${e.message}")
            0
        }
    }

    fun incrementMigrationRetryCount() {
        val prefs = sharedPreferences ?: return
        try {
            val current = prefs.getInt(MIGRATION_RETRY_COUNT_KEY, 0)
            prefs.edit().putInt(MIGRATION_RETRY_COUNT_KEY, current + 1).apply()
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to increment migration retry count", e.toString())
        }
    }
}
