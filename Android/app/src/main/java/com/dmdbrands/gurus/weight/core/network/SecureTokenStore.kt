package com.dmdbrands.gurus.weight.core.network

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.api.user.Token
import com.google.gson.Gson
import java.security.GeneralSecurityException

class SecureTokenStore(context: Context) : ISecureTokenStore {

    companion object {
        private const val TAG = "SecureTokenStore"
        private const val PREFS_FILE_NAME = "secure_tokens"
        private const val TOKEN_KEY_PREFIX = "token_"
        private const val MIGRATION_COMPLETED_KEY = "migration_completed"
    }

    private val gson = Gson()
    private val sharedPreferences: SharedPreferences

    init {
        sharedPreferences = try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: GeneralSecurityException) {
            AppLog.e(TAG, "Failed to create EncryptedSharedPreferences, falling back to regular prefs", e.toString())
            context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
        }
    }

    override fun saveToken(accountId: String, token: Token) {
        try {
            val json = gson.toJson(token)
            sharedPreferences.edit()
                .putString("$TOKEN_KEY_PREFIX$accountId", json)
                .apply()
            AppLog.v(TAG, "Saved token for account: $accountId")
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to save token for account: $accountId", e.toString())
        }
    }

    override fun getToken(accountId: String): Token? {
        return try {
            val json = sharedPreferences.getString("$TOKEN_KEY_PREFIX$accountId", null)
            if (json != null) gson.fromJson(json, Token::class.java) else null
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to get token for account: $accountId", e.toString())
            null
        }
    }

    override fun getAllTokens(): Map<String, Token> {
        val tokens = mutableMapOf<String, Token>()
        try {
            sharedPreferences.all.forEach { (key, value) ->
                if (key.startsWith(TOKEN_KEY_PREFIX) && value is String) {
                    val accountId = key.removePrefix(TOKEN_KEY_PREFIX)
                    val token = gson.fromJson(value, Token::class.java)
                    if (token != null) {
                        tokens[accountId] = token
                    }
                }
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to get all tokens", e.toString())
        }
        return tokens
    }

    override fun removeToken(accountId: String) {
        try {
            sharedPreferences.edit()
                .remove("$TOKEN_KEY_PREFIX$accountId")
                .apply()
            AppLog.v(TAG, "Removed token for account: $accountId")
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to remove token for account: $accountId", e.toString())
        }
    }

    override fun clearAll() {
        try {
            val editor = sharedPreferences.edit()
            sharedPreferences.all.keys
                .filter { it.startsWith(TOKEN_KEY_PREFIX) }
                .forEach { editor.remove(it) }
            editor.apply()
            AppLog.v(TAG, "Cleared all tokens")
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to clear all tokens", e.toString())
        }
    }

    override fun hasTokens(): Boolean {
        return try {
            sharedPreferences.all.keys.any { it.startsWith(TOKEN_KEY_PREFIX) }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to check for tokens", e.toString())
            false
        }
    }

    fun isMigrationCompleted(): Boolean {
        return sharedPreferences.getBoolean(MIGRATION_COMPLETED_KEY, false)
    }

    fun setMigrationCompleted() {
        sharedPreferences.edit().putBoolean(MIGRATION_COMPLETED_KEY, true).apply()
    }
}
