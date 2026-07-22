package com.dmdbrands.gurus.weight.core.network

import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import com.dmdbrands.gurus.weight.domain.model.api.user.Token
import com.dmdbrands.gurus.weight.domain.services.AuthState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenMigrationHelper @Inject constructor(
    private val secureTokenStore: SecureTokenStore,
    private val userDataStore: UserDataStore,
    private val appNavigationService: IAppNavigationService,
) {
    companion object {
        private const val TAG = "TokenMigrationHelper"
        private const val MAX_MIGRATION_RETRIES = 3
    }

    suspend fun migrateIfNeeded() {
        // Check if encrypted storage is available at all
        if (!secureTokenStore.isAvailable) {
            handleStorageUnavailable()
            return
        }

        try {
            if (secureTokenStore.isMigrationCompleted()) {
                AppLog.v(TAG, "Token migration already completed, skipping")
                return
            }
        } catch (e: EncryptionUnavailableException) {
            AppLog.e(TAG, "Cannot check migration status — encryption unavailable", e.toString())
            return
        }

        AppLog.i(TAG, "Starting token migration from DataStore to EncryptedSharedPreferences")
        performTokenMigration()
    }

    /** Logs the unavailable-storage retry state and forces re-login once retries are exhausted. */
    private suspend fun handleStorageUnavailable() {
        val retryCount = secureTokenStore.getMigrationRetryCount()
        AppLog.e(TAG, "Encrypted storage unavailable — retry $retryCount/$MAX_MIGRATION_RETRIES")
        if (retryCount >= MAX_MIGRATION_RETRIES) {
            AppLog.e(TAG, "Migration permanently failed after $retryCount attempts — forcing re-login")
            appNavigationService.emitAuthEvent(AuthState.EncryptionFailure(null))
        }
    }

    /** Runs the DataStore→EncryptedSharedPreferences token migration with retry/failure handling. */
    private suspend fun performTokenMigration() {
        try {
            val allAccounts = userDataStore.getData().accountsMap

            if (allAccounts.isEmpty()) {
                AppLog.v(TAG, "No accounts found in DataStore, marking migration as complete")
                secureTokenStore.setMigrationCompleted()
                return
            }

            val result = migrateAndVerifyTokens(allAccounts)

            if (result.verificationFailed) {
                onMigrationVerificationFailed()
                return
            }

            // All tokens verified — now safe to clear from DataStore
            allAccounts.keys.forEach { accountId ->
                userDataStore.updateAccount(
                    accountId = accountId,
                    accessToken = "",
                    refreshToken = "",
                    expiresAt = "",
                )
            }

            secureTokenStore.setMigrationCompleted()
            AppLog.i(TAG, "Token migration completed successfully. Migrated ${result.migratedCount} accounts.")
        } catch (e: EncryptionUnavailableException) {
            secureTokenStore.incrementMigrationRetryCount()
            val retryCount = secureTokenStore.getMigrationRetryCount()
            AppLog.e(TAG, "Token migration failed — encryption unavailable, retry $retryCount/$MAX_MIGRATION_RETRIES", e.toString())
            if (retryCount >= MAX_MIGRATION_RETRIES) {
                appNavigationService.emitAuthEvent(AuthState.EncryptionFailure(null))
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Token migration failed", e.toString())
            // Don't set migration completed so it retries next launch
        }
    }

    /** Outcome of migrating each account's token: how many migrated + whether any verify failed. */
    private data class TokenMigrationResult(val migratedCount: Int, val verificationFailed: Boolean)

    /** Saves + verifies each account's token into encrypted storage; stops marking on first failure. */
    private suspend fun migrateAndVerifyTokens(
        allAccounts: Map<String, com.dmdbrands.gurus.weight.proto.UserAccount>,
    ): TokenMigrationResult {
        var migratedCount = 0
        var verificationFailed = false

        allAccounts.forEach { (accountId, userAccount) ->
            val token = Token(
                accountId = accountId,
                isActive = userAccount.isActive,
                accessToken = userAccount.accessToken,
                refreshToken = userAccount.refreshToken,
                expiresAt = userAccount.expiresAt,
            )

            // Only migrate if there are actual token values
            if (!token.accessToken.isNullOrEmpty() || !token.refreshToken.isNullOrEmpty()) {
                secureTokenStore.saveToken(accountId, token)

                // VERIFY the write is readable before deleting old data
                val verified = secureTokenStore.getToken(accountId)
                if (verified != null && verified.accessToken == token.accessToken) {
                    migratedCount++
                    AppLog.v(TAG, "Migrated and verified token for account: $accountId")
                } else {
                    AppLog.e(TAG, "Migration verification failed for account: $accountId")
                    verificationFailed = true
                    return@forEach
                }
            }
        }
        return TokenMigrationResult(migratedCount, verificationFailed)
    }

    /** Handles a failed migration verification: increment retry and force re-login when exhausted. */
    private suspend fun onMigrationVerificationFailed() {
        secureTokenStore.incrementMigrationRetryCount()
        val retryCount = secureTokenStore.getMigrationRetryCount()
        AppLog.e(TAG, "Migration verification failed — retry $retryCount/$MAX_MIGRATION_RETRIES")
        if (retryCount >= MAX_MIGRATION_RETRIES) {
            AppLog.e(TAG, "Migration permanently failed — forcing re-login")
            appNavigationService.emitAuthEvent(AuthState.EncryptionFailure(null))
        }
    }
}
