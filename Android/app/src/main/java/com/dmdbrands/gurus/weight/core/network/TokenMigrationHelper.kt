package com.dmdbrands.gurus.weight.core.network

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import com.dmdbrands.gurus.weight.domain.model.api.user.Token
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenMigrationHelper @Inject constructor(
    private val secureTokenStore: SecureTokenStore,
    private val userDataStore: UserDataStore,
) {
    companion object {
        private const val TAG = "TokenMigrationHelper"
    }

    suspend fun migrateIfNeeded() {
        if (secureTokenStore.isMigrationCompleted()) {
            AppLog.v(TAG, "Token migration already completed, skipping")
            return
        }

        AppLog.i(TAG, "Starting token migration from DataStore to EncryptedSharedPreferences")

        try {
            val allAccounts = userDataStore.getData().accountsMap

            if (allAccounts.isEmpty()) {
                AppLog.v(TAG, "No accounts found in DataStore, marking migration as complete")
                secureTokenStore.setMigrationCompleted()
                return
            }

            var migratedCount = 0
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
                    migratedCount++
                    AppLog.v(TAG, "Migrated token for account: $accountId")
                }
            }

            // Clear only token values from DataStore (keep isActive, themeMode, syncTimestamp)
            allAccounts.keys.forEach { accountId ->
                userDataStore.updateAccount(
                    accountId = accountId,
                    accessToken = "",
                    refreshToken = "",
                    expiresAt = "",
                )
            }

            secureTokenStore.setMigrationCompleted()
            AppLog.i(TAG, "Token migration completed successfully. Migrated $migratedCount accounts.")
        } catch (e: Exception) {
            AppLog.e(TAG, "Token migration failed", e.toString())
            // Don't set migration completed so it retries next launch
        }
    }
}
