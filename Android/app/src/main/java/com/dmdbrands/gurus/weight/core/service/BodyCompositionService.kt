package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.config.HttpErrorConfig
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.WeightCompSettingsEntity
import com.dmdbrands.gurus.weight.domain.enums.AccountSettingsAction
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.api.user.BodyCompUpdateRequest
import com.dmdbrands.gurus.weight.domain.repository.IBodyCompositionRepository
import com.dmdbrands.gurus.weight.domain.services.BodyCompUpdateType
import com.dmdbrands.gurus.weight.domain.services.IBodyCompositionService
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.strings.ToastStrings
import com.dmdbrands.gurus.weight.features.export.strings.ExportStrings
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of body composition service for managing body composition settings.
 * Handles activity level, weight unit, and height updates with offline support.
 */
@Singleton
class BodyCompositionService
@Inject
constructor(
  private val bodyCompositionRepository: IBodyCompositionRepository,
  connectivityObserver: IConnectivityObserver,
  dialogQueueService: IDialogQueueService,
  appNavigationService: IAppNavigationService,
) : BaseService(connectivityObserver, dialogQueueService, appNavigationService), IBodyCompositionService {
  companion object {
    private const val TAG = "BodyCompositionService"
  }

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
  ) {
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
        bodyCompositionRepository.updateBodyCompInDB(activeAccount.id, bodyCompEntity)
        AppLog.i(TAG, "Body composition saved to DB with isSynced = true")
      } else {
        val bodyCompEntity =
          WeightCompSettingsEntity(
            accountId = activeAccount.id,
            height = bodyComposition.height,
            activityLevel = bodyComposition.activityLevel,
            weightUnit = bodyComposition.weightUnit,
            isSynced = false,
          )
        bodyCompositionRepository.updateBodyCompInDB(activeAccount.id, bodyCompEntity)
        AppLog.i(TAG, "Body composition saved to DB with isSynced = false for offline sync")
      }
    } catch (e: Exception) {
      AppLog.e(TAG, "Body composition update failed", e)
      null
    } finally {
      showSuccessToast(
        ToastStrings.Success.UpdateProfileSuccess.Header,
        ToastStrings.Success.UpdateProfileSuccess.Message,
      )
    }
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
