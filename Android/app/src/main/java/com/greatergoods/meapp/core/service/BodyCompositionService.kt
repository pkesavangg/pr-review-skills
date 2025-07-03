package com.greatergoods.meapp.core.service

import com.greatergoods.meapp.core.config.HttpErrorConfig
import com.greatergoods.meapp.core.network.interfaces.IConnectivityObserver
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.data.storage.db.entity.account.WeightCompSettingsEntity
import com.greatergoods.meapp.domain.enums.AccountSettingsAction
import com.greatergoods.meapp.domain.interfaces.IDialogQueueService
import com.greatergoods.meapp.domain.model.api.user.BodyCompUpdateRequest
import com.greatergoods.meapp.domain.model.storage.Account.Account
import com.greatergoods.meapp.domain.repository.IBodyCompositionRepository
import com.greatergoods.meapp.domain.services.BodyCompUpdateType
import com.greatergoods.meapp.domain.services.IBodyCompositionService
import com.greatergoods.meapp.features.common.model.Toast
import com.greatergoods.meapp.features.common.strings.ToastStrings
import com.greatergoods.meapp.features.export.strings.ExportStrings
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of body composition service for managing body composition settings.
 * Handles activity level, weight unit, and height updates with offline support.
 * Follows the same pattern as Angular updateBodycomp method.
 */
@Singleton
class BodyCompositionService
    @Inject
    constructor(
        private val bodyCompositionRepository: IBodyCompositionRepository,
        private val connectivityObserver: IConnectivityObserver,
        private val dialogQueueService: IDialogQueueService,
    ) : IBodyCompositionService {
        companion object {
            private const val TAG = "BodyCompositionService"
        }

        /**
         * Checks if network is available for API calls.
         */
        private fun isNetworkAvailable(): Boolean = !connectivityObserver.getCurrentNetworkState().unAvailable

        /**
         * Updates body composition data both online and offline.
         * Online: Updates via API, then saves to DB with isSynced = true
         * Offline: Saves to DB with isSynced = false for later sync
         *
         * @param updateType The type of update (activity level, weight unit, or height)
         * @param bodyCompRequest The body composition data to update
         * @return The updated account or null if update fails
         */
        override suspend fun updateBodyComposition(
            updateType: BodyCompUpdateType,
            bodyComposition: BodyCompUpdateRequest,
        ): Account? =
            try {
                val activeAccount =
                    bodyCompositionRepository.getActiveAccountFromDB()
                        ?: throw IllegalStateException("No active account found")

                if (isNetworkAvailable()) {
                    val response = bodyCompositionRepository.updateBodyCompInAPI(bodyComposition)
                    val bodyCompEntity =
                        WeightCompSettingsEntity(
                            accountId = activeAccount.id,
                            height = response.account.height,
                            activityLevel = response.account.activityLevel,
                            weightUnit = response.account.weightUnit,
                            isSynced = true,
                        )
                    val updatedAccount = bodyCompositionRepository.updateBodyCompInDB(activeAccount.id, bodyCompEntity)
                    AppLog.i(TAG, "Body composition saved to DB with isSynced = true")
                    updatedAccount
                } else {
                    val bodyCompEntity =
                        WeightCompSettingsEntity(
                            accountId = activeAccount.id,
                            height = bodyComposition.height,
                            activityLevel = bodyComposition.activityLevel,
                            weightUnit = bodyComposition.weightUnit,
                            isSynced = false,
                        )
                    val updatedAccount = bodyCompositionRepository.updateBodyCompInDB(activeAccount.id, bodyCompEntity)
                    AppLog.i(TAG, "Body composition saved to DB with isSynced = false for offline sync")
                    updatedAccount
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Body composition update failed", e.toString())
                null
            }

        /**
         * Shows success toast for export operation.
         */
        private fun successToast() {
            dialogQueueService.showToast(
                Toast(
                    message = ExportStrings.SuccessMessage,
                ),
            )
        }

        fun showErrorToast(
            action: AccountSettingsAction,
            error: HttpException?,
        ) {
            val (title, message) =
                when (action) {
                    AccountSettingsAction.EXPORT_CSV -> {
                        val header = ""
                        val message =
                            when (error?.code()) {
                                HttpErrorConfig.ResponseCode.NO_INTERNET_CONNECTION ->
                                    ToastStrings.Error.LoginError.MessageNoConn

                                HttpErrorConfig.ResponseCode.INTERNAL_SERVER_ERROR ->
                                    ToastStrings.Error.LoginError.MessageServError

                                HttpErrorConfig.ResponseCode.UNAUTHORIZED ->
                                    ToastStrings.Error.LoginError.MessageNotAuth

                                else ->
                                    ToastStrings.Error.LoginError.MessageGeneric
                            }
                        header to message
                    }
                }
            val errorToast =
                Toast(
                    title = title,
                    message = message,
                    action = null,
                )
            dialogQueueService.showToast(errorToast)
        }
    }
