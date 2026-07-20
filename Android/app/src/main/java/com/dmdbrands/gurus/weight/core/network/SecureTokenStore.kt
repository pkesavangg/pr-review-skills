package com.dmdbrands.gurus.weight.core.network

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.api.user.Token
import com.google.gson.Gson
import java.security.GeneralSecurityException
import java.security.KeyStore

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

class SecureTokenStore(
    context: Context,
    // Injectable so the create/recover orchestration can be unit-tested without the Android
    // keystore. Production uses [createDefaultEncryptedPrefs]; tests supply a factory that throws
    // then succeeds to exercise the recovery path.
    private val encryptedPrefsFactory: (Context) -> SharedPreferences = ::createDefaultEncryptedPrefs,
) : ISecureTokenStore {

    companion object {
        private const val TAG = "SecureTokenStore"
        private const val PREFS_FILE_NAME = "secure_tokens"
        private const val META_PREFS_FILE_NAME = "secure_tokens_meta"
        private const val TOKEN_KEY_PREFIX = "token_"
        private const val MIGRATION_COMPLETED_KEY = "migration_completed"
        private const val MIGRATION_RETRY_COUNT_KEY = "migration_retry_count"
        private const val ENCRYPTION_FAILURE_COUNT_KEY = "encryption_failure_count"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"

        /** Default (production) encrypted-prefs creation: a get-or-create master key + the store. */
        private fun createDefaultEncryptedPrefs(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }

    private val gson = Gson()
    private val sharedPreferences: SharedPreferences?

    /**
     * Plain (unencrypted) prefs for counters that MUST survive the encrypted store being
     * unavailable. The retry/failure caps are meaningless if they live in the encrypted file,
     * because that file is exactly what's missing when [isAvailable] is false — the count would
     * always read back as 0 and the safeguard would never trip, allowing an infinite re-login
     * loop (MOB-1537 / MOB-1526). These hold no secrets, only small integers.
     */
    private val metaPrefs: SharedPreferences =
        context.getSharedPreferences(META_PREFS_FILE_NAME, Context.MODE_PRIVATE)

    /**
     * Whether encrypted storage is available.
     * If false, all token operations will throw [EncryptionUnavailableException].
     */
    val isAvailable: Boolean get() = sharedPreferences != null

    init {
        sharedPreferences = createOrRecoverPrefs(context)
    }

    /**
     * Creates the encrypted store, and on failure attempts a one-time recovery before giving up.
     * The failure that matters in the field is an *invalidated* master key (OS update, backup
     * restore to different hardware, lock-screen change) — the exact cases in this class's KDoc.
     * When that happens the old key can no longer decrypt [PREFS_FILE_NAME], so
     * [EncryptedSharedPreferences.create] throws [GeneralSecurityException] (or a keystore
     * subtype of it, e.g. [java.security.KeyStoreException] /
     * [javax.crypto.AEADBadTagException]) and, previously, the store stayed null for the whole
     * session — every token op threw and the user was silently logged out with no way back short
     * of clearing app storage. (MOB-1598)
     *
     * Recovery is deliberately narrow: only [GeneralSecurityException] is treated as a
     * key-invalidation signal. [EncryptedSharedPreferences.create] is also declared to throw
     * [java.io.IOException] for transient conditions (disk full, temporary file lock, concurrent
     * access) that have nothing to do with the key — those are NOT key-invalidation cases, and
     * recovering from them would wipe otherwise-valid tokens and force an unnecessary re-login,
     * the opposite of this class's goal. Any such non-security failure just leaves the store
     * unavailable for this construction, without touching the keystore entry or the prefs file,
     * so a later construction attempt can still open the existing store once the condition
     * clears.
     */
    private fun createOrRecoverPrefs(context: Context): SharedPreferences? =
        try {
            encryptedPrefsFactory(context)
        } catch (e: GeneralSecurityException) {
            AppLog.e(TAG, "Failed to create EncryptedSharedPreferences — attempting recovery", e.toString())
            recoverEncryptedPrefs(context)
        } catch (e: Exception) {
            // IOException (transient disk error, low storage, temporary file lock, concurrent
            // access) or any other non-security exception is not a key-invalidation case — do NOT
            // wipe the keystore entry or the secure_tokens file for it.
            AppLog.e(TAG, "Non-security error creating EncryptedSharedPreferences — not recovering", e.toString())
            null
        }

    /**
     * Clears the undecryptable prefs file and the invalidated master-key entry, then recreates the
     * store once — the in-code equivalent of wiping the app's keystore namespace, which is the only
     * thing that fixes an invalidated key. Tokens in the old file are unrecoverable anyway (they
     * can't be decrypted), so dropping them costs at most one re-login and restores persistent auth
     * instead of logging the user out every session. Runs at most once per construction, so a
     * genuinely broken keystore can't loop.
     *
     * On success the encryption-failure safeguard counter is *incremented*, not reset — a recovery
     * still means this session paid the wipe/re-login cost, so it stays part of the failure/retry
     * cycle the counter tracks. If some devices have their key invalidated on every launch (seen on
     * some Samsung/OS-update cases), each session would otherwise recover successfully, silently
     * wipe [PREFS_FILE_NAME], force a re-login, and reset the counter to 0 — making the exact
     * repeating wipe/re-login loop the counter exists to bound invisible to it. The counter is only
     * reset to 0 by [TokenManager.setTokens] after a token is actually saved successfully
     * post-login, which is the real proof that encryption works again on this device — preserving
     * the MOB-1537 / MOB-1526 loop guard. (MOB-1598)
     */
    private fun recoverEncryptedPrefs(context: Context): SharedPreferences? =
        try {
            deleteCorruptKeystoreState(context)
            encryptedPrefsFactory(context).also {
                AppLog.i(TAG, "Recovered EncryptedSharedPreferences after master-key reset")
                incrementEncryptionFailureCount()
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Recovery of EncryptedSharedPreferences failed", e.toString())
            null
        }

    private fun deleteCorruptKeystoreState(context: Context) {
        // Drop the undecryptable prefs file so a fresh one is written under the new key.
        context.deleteSharedPreferences(PREFS_FILE_NAME)
        // Remove the invalidated master key so MasterKey.Builder regenerates a usable one.
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(MasterKey.DEFAULT_MASTER_KEY_ALIAS)) {
                keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to delete master key during recovery", e.toString())
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
     * Gets the number of migration retry attempts. Backed by plain prefs so the count is readable
     * even when the encrypted store is unavailable (that's precisely when the cap must hold).
     */
    fun getMigrationRetryCount(): Int =
        metaPrefs.getInt(MIGRATION_RETRY_COUNT_KEY, 0)

    fun incrementMigrationRetryCount() {
        val current = metaPrefs.getInt(MIGRATION_RETRY_COUNT_KEY, 0)
        metaPrefs.edit().putInt(MIGRATION_RETRY_COUNT_KEY, current + 1).apply()
    }

    override fun getEncryptionFailureCount(): Int =
        metaPrefs.getInt(ENCRYPTION_FAILURE_COUNT_KEY, 0)

    override fun incrementEncryptionFailureCount() {
        val current = metaPrefs.getInt(ENCRYPTION_FAILURE_COUNT_KEY, 0)
        metaPrefs.edit().putInt(ENCRYPTION_FAILURE_COUNT_KEY, current + 1).apply()
    }

    override fun resetEncryptionFailureCount() {
        metaPrefs.edit().putInt(ENCRYPTION_FAILURE_COUNT_KEY, 0).apply()
    }
}
