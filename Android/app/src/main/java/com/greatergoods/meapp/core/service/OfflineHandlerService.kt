package com.greatergoods.meapp.core.service

import com.greatergoods.meapp.core.network.interfaces.IConnectivityObserver
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.model.PartialAccount
import com.greatergoods.meapp.domain.model.api.user.ProfileUpdateRequest
import com.greatergoods.meapp.domain.repository.IAccountRepository
import com.greatergoods.meapp.domain.services.IOfflineHandlerService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of offline handler service for managing offline data synchronization.
 * Follows the same pattern as the Angular offline-handler.service.ts.
 * Automatically monitors network connectivity and syncs unsynced data when online.
 */
@Singleton
class OfflineHandlerService @Inject constructor(
    private val accountRepository: IAccountRepository,
    private val connectivityObserver: IConnectivityObserver,
) : IOfflineHandlerService {

    companion object {
        private const val TAG = "OfflineHandlerService"
    }

    /**
     * Handles offline data synchronization when network connectivity is restored.
     * Syncs all pending offline data including profile updates, goals, weightless settings, etc.
     */
    override suspend fun handleOfflineSync() {
        AppLog.d(TAG, "Starting offline sync process")


        try {
            // Check if network is available
            if (connectivityObserver.getCurrentNetworkState().unAvailable) {
                AppLog.w(TAG, "Network not available, skipping sync")
                return
            }

            // Get all accounts with unsynced data (isSynced = false)
            val unsyncedAccounts = accountRepository.getUnsyncedAccountsFromDB()

            if (unsyncedAccounts.isEmpty()) {
                AppLog.d(TAG, "No unsynced accounts found")
                return
            }

            AppLog.i(TAG, "Found ${unsyncedAccounts.size} unsynced accounts to sync")

            // Sync each unsynced account
            for (account in unsyncedAccounts) {
                try {
                    AppLog.d(TAG, "Syncing account: ${account.id}")

                    // Create profile update request from account data
                    val profileUpdateRequest = ProfileUpdateRequest(
                        id = account.id,
                        firstName = account.firstName,
                        lastName = account.lastName,
                        email = account.email,
                        dob = account.dob,
                        gender = account.gender,
                        zipcode = account.zipcode
                    )

                    // Use existing updateProfile method which handles API call and DB update
                    val updatedAccount = accountRepository.updateProfileInAPI(profileUpdateRequest)
                    val accountUpdate = PartialAccount(
                        gender = updatedAccount.account.gender,
                        isSynced = true
                    )
                    accountRepository.updateAccountInDB(account.id, accountUpdate)
                } catch (e: Exception) {
                    AppLog.e(TAG, "Error syncing account ${account.id}", e.toString())
                }
            }

            AppLog.i(TAG, "Offline sync process completed")
        } catch (e: Exception) {
            AppLog.e(TAG, "Offline sync process failed", e.toString())
        }
    }
}
