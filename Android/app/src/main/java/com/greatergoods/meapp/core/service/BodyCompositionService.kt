package com.greatergoods.meapp.core.service

import com.greatergoods.meapp.core.network.interfaces.IConnectivityObserver
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.data.storage.db.entity.account.WeightCompSettingsEntity
import com.greatergoods.meapp.domain.model.api.user.BodyCompUpdateRequest
import com.greatergoods.meapp.domain.model.storage.Account.Account
import com.greatergoods.meapp.domain.repository.IBodyCompositionRepository
import com.greatergoods.meapp.domain.services.BodyCompUpdateType
import com.greatergoods.meapp.domain.services.IBodyCompositionService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service implementation for managing body composition settings.
 * Handles activity level, weight unit, and height updates with offline support.
 * Follows the same pattern as Angular updateBodycomp method.
 */
@Singleton
class BodyCompositionService @Inject constructor(
    private val bodyCompositionRepository: IBodyCompositionRepository,
    private val connectivityObserver: IConnectivityObserver,
) : IBodyCompositionService {

    companion object {
        private const val TAG = "BodyCompositionService"
    }

    /**
     * Checks if network is available using the connectivity observer
     */
    private fun isNetworkAvailable(): Boolean = !connectivityObserver.getCurrentNetworkState().unAvailable


    /**
     * Updates body composition data in the local database.
     * This method handles offline updates for any body composition field.
     *
     * @param updateType The type of update (activity level, weight unit, or height)
     * @param bodyComposition The new value (String for activity level, WeightUnit for weight unit, Int for height)
     * @return The updated account or null if update fails
     */
    override suspend fun updateBodyComposition(updateType: BodyCompUpdateType, bodyComposition: BodyCompUpdateRequest): Account? {
        return try {
            val activeAccount = bodyCompositionRepository.getActiveAccountFromDB()
                ?: throw IllegalStateException("No active account found")

            if (isNetworkAvailable()) {
                val response = bodyCompositionRepository.updateBodyCompInAPI(bodyComposition)
                val bodyComposition = WeightCompSettingsEntity(
                        accountId = activeAccount.id,
                        height = response.account.height,
                        activityLevel = response.account.activityLevel,
                        weightUnit = response.account.weightUnit,
                        isSynced = true
                )
                bodyCompositionRepository.updateBodyCompInDB(activeAccount.id, bodyComposition)
                AppLog.i(TAG, "$updateType updated online for account: ${activeAccount.id}")
                // Note: Account sync will be handled by OfflineHandlerService
                null
            } else {
                // Offline: Store locally using body composition repository
                val bodyComposition = WeightCompSettingsEntity(
                    accountId = activeAccount.id,
                    height = bodyComposition.height,
                    activityLevel = bodyComposition.activityLevel,
                    weightUnit = bodyComposition.weightUnit,
                    isSynced = false
                )
                bodyCompositionRepository.updateBodyCompInDB(activeAccount.id,bodyComposition)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to update $updateType", e.toString())
            null
        }
    }
}
