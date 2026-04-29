package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.api.IUserSettingsAPI
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import com.dmdbrands.gurus.weight.data.storage.db.dao.AccountDao
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.AccountEntityMapper
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.StreaksSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.WeightlessSettingsEntity
import com.dmdbrands.gurus.weight.domain.model.api.metrics.StreakRequest
import com.dmdbrands.gurus.weight.domain.model.api.metrics.WeightlessRequest
import com.dmdbrands.gurus.weight.domain.model.api.user.AccountResponse
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.repository.IUserSettingsRepository
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.enums.toDefaultGraphSegment
import com.dmdbrands.gurus.weight.features.common.enums.toGraphSegment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository implementation for user settings operations.
 * Handles streak and weightless mode settings with API and database operations.
 */
@Singleton
class UserSettingsRepository
    @Inject
    constructor(
        private val userSettingsAPI: IUserSettingsAPI,
        private val accountDao: AccountDao,
        private val userDataStore: UserDataStore,
    ) : IUserSettingsRepository {
        private val TAG = "UserSettingsRepository"

        /**
         * Updates the streak setting for the active account.
         * @param streakRequest The streak setting to update
         * @return Updated account with new streak settings
         */
        override suspend fun updateStreakSetting(streakRequest: StreakRequest) {
          try {
            val response: AccountResponse = userSettingsAPI.updateStreak(streakRequest)
            val streaksSettingsEntity =
              StreaksSettingsEntity(
                accountId = response.account.id,
                isStreakOn = response.account.isStreakOn,
                streakTimestamp = System.currentTimeMillis().toString(),
                isSynced = true,
              )
            accountDao.updateStreaksSettings(streaksSettingsEntity)
            // Return updated account
          } catch (e: Exception) {
            // Handle offline mode - update local database only
            val activeAccount = accountDao.getActiveAccount().first()
            activeAccount?.let { account ->
              val streaksSettingsEntity =
                StreaksSettingsEntity(
                  accountId = account.account.id,
                  isStreakOn = streakRequest.isStreakOn,
                  streakTimestamp = streakRequest.streakTimestamp ?: System.currentTimeMillis().toString(),
                  isSynced = false,
                )
              accountDao.updateStreaksSettings(streaksSettingsEntity)
              AppLog.e(TAG, "Error updating streak setting to server", e)

            }
          }
        }


        /**
         * Updates the weightless setting for the active account.
         * @param weightlessRequest The weightless setting to update
         * @return Updated account with new weightless settings
         */
        override suspend fun updateWeightlessSetting(weightlessRequest: WeightlessRequest)  {
          try {
            AppLog.d(TAG, "Updating weightless setting: $weightlessRequest")
            val response: AccountResponse = userSettingsAPI.updateWeightless(weightlessRequest)
            // Update local database
            val weightlessSettingsEntity =
              WeightlessSettingsEntity(
                accountId = response.account.id,
                isWeightlessOn = response.account.isWeightlessOn,
                weightlessTimestamp =
                  response.account.weightlessTimestamp ?: System
                    .currentTimeMillis()
                    .toString(),
                weightlessWeight = response.account.weightlessWeight ?: 0.0f,
                isSynced = true,
              )
            accountDao.updateWeightlessSettings(weightlessSettingsEntity)
          } catch (e: Exception) {
            AppLog.e(TAG, "Error updating weightless setting", e)

            // Handle offline mode - update local database only
            val activeAccount = accountDao.getActiveAccount().first()
            activeAccount?.let { account ->
              val weightlessSettingsEntity =
                WeightlessSettingsEntity(
                  accountId = account.account.id,
                  isWeightlessOn = weightlessRequest.isWeightlessOn,
                  weightlessTimestamp =
                    weightlessRequest.weightlessTimestamp ?: System
                      .currentTimeMillis()
                      .toString(),
                  weightlessWeight = weightlessRequest.weightlessWeight?.toFloat() ?: 0.0f,
                  isSynced = false,
                )
              accountDao.updateWeightlessSettings(weightlessSettingsEntity)
            }
          }
        }


        /**
         * Updates streak setting offline (stores locally for later sync).
         * Used when network is unavailable.
         * @param request The streak setting request
         * @return Updated account with new streak settings
         */
        override suspend fun updateStreakSettingOffline(request: StreakRequest): Account? =
            try {
                AppLog.d(TAG, "Updating streak setting offline: $request")

                val activeAccount = accountDao.getActiveAccount().first()
                activeAccount?.let { account ->
                    val streaksSettingsEntity =
                        StreaksSettingsEntity(
                            accountId = account.account.id,
                            isStreakOn = request.isStreakOn,
                            streakTimestamp = request.streakTimestamp ?: System.currentTimeMillis().toString(),
                            isSynced = false, // Mark as unsynced for offline mode
                        )
                    accountDao.updateStreaksSettings(streaksSettingsEntity)

                    // Return updated account
                    val updatedAccount = accountDao.getActiveAccount().first()
                    updatedAccount?.let { AccountEntityMapper.toDomainFromAccountWithRelations(it) }
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Error updating streak setting offline", e)
                null
            }

        /**
         * Updates weightless setting offline (stores locally for later sync).
         * Used when network is unavailable.
         * @param request The weightless setting request
         * @return Updated account with new weightless settings
         */
        override suspend fun updateWeightlessSettingOffline(request: WeightlessRequest): Account? =
            try {
                AppLog.d(TAG, "Updating weightless setting offline: $request")

                val activeAccount = accountDao.getActiveAccount().first()
              if(activeAccount !== null){
                activeAccount.let { account ->
                  val weightlessSettingsEntity =
                    WeightlessSettingsEntity(
                      accountId = account.account.id,
                      isWeightlessOn = request.isWeightlessOn, // Default to false if null
                      weightlessTimestamp = request.weightlessTimestamp ?: System.currentTimeMillis().toString(),
                      weightlessWeight = request.weightlessWeight?.toFloat() ?: 0.0f,
                      isSynced = false, // Mark as unsynced for offline mode
                    )
                  accountDao.updateWeightlessSettings(weightlessSettingsEntity)

                  // Return updated account
                  val updatedAccount = accountDao.getActiveAccount().first()
                  updatedAccount?.let { AccountEntityMapper.toDomainFromAccountWithRelations(it) }
                }
              } else {
                null
              }
            } catch (e: Exception) {
                AppLog.e(TAG, "Error updating weightless setting offline", e)
                null
            }

        /**
         * Gets the active account if it has unsynced streak settings.
         */
        override suspend fun getUnsyncedActiveStreakAccountFromDB(): Account? {
            val unsyncedActiveAccount = accountDao.getUnsyncedActiveStreakAccount().first()
            return unsyncedActiveAccount?.let {
                AccountEntityMapper.toDomainFromAccountWithRelations(it)
            }
        }

        /**
         * Gets the active account if it has unsynced weightless settings.
         */
        override suspend fun getUnsyncedActiveWeightlessAccountFromDB(): Account? {
            val unsyncedActiveAccount = accountDao.getUnsyncedActiveWeightlessAccount().first()
            return unsyncedActiveAccount?.let {
                AccountEntityMapper.toDomainFromAccountWithRelations(it)
            }
        }

        override val defaultGraphSegmentFlow: Flow<GraphSegment> =
            userDataStore.defaultGraphSegmentFlow
                .map { it.toGraphSegment() }
                .distinctUntilChanged()

        override suspend fun setDefaultGraphSegment(segment: GraphSegment) {
            val accountId = userDataStore.currentAccountIdFlow.first()
                ?: error("No active account when persisting default graph segment")
            userDataStore.setDefaultGraphSegment(accountId, segment.toDefaultGraphSegment())
        }
    }
