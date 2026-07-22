package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.repository.IHealthConnectRepository
import com.dmdbrands.gurus.weight.domain.repository.IIntegrationRepository
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.integration.model.Integrations
import com.dmdbrands.gurus.weight.features.integration.strings.HealthConnectStrings
import com.greatergoods.libs.healthconnect.enums.HealthConnectPermissionStatus
import com.greatergoods.libs.healthconnect.enums.HealthConnectStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Out-of-sync + permission-disabled orchestration slice extracted from [HealthConnectService]
 * (MOB-1500). Drives [healthConnectOutOfSync] and [checkHealthConnectPermissionDisabled] and the
 * modal flows they fan out to. Cross-cutting operations still owned by [HealthConnectService]
 * (status/permission queries, integration lifecycle, multi-device reconnect) are supplied as
 * callbacks; shared mutable state (account id, current integrations, out-of-sync flow) via
 * getters/setters. Behaviour-preserving verbatim move.
 */
class HealthConnectOutOfSyncManager(
  private val getCurrentAccountId: () -> String?,
  private val getCurrentIntegrations: () -> Integrations?,
  private val healthConnectStatus: suspend () -> HealthConnectStatus,
  private val checkPermissionStatus: suspend () -> HealthConnectPermissionStatus,
  private val checkIfAlreadyUsed: suspend () -> Boolean,
  private val checkPermissionChange: suspend () -> Unit,
  private val turnOnIntegration: suspend (Boolean, Boolean) -> Unit,
  private val checkMultiDeviceConnection: suspend (Boolean) -> Boolean,
  private val removeHealthConnectIntegration: suspend () -> Boolean,
  private val openHealthConnect: suspend () -> Boolean,
  private val setOutOfSyncState: (Boolean) -> Unit,
  private val healthConnectRepository: IHealthConnectRepository,
  private val integrationRepository: IIntegrationRepository,
  private val dialogQueueService: IDialogQueueService,
  private val appNavigationService: IAppNavigationService,
  private val appScope: CoroutineScope,
) {

  private val tag = "HealthConnectService"
  private val currentAccountId: String? get() = getCurrentAccountId()
  private val currentIntegrations: Integrations? get() = getCurrentIntegrations()

  suspend fun healthConnectOutOfSync(): Boolean {
    return try {
      val accountId = currentAccountId
      if (accountId == null) {
        AppLog.w(tag, "No active account found")
        return false
      }

      val healthConnectStatus = healthConnectStatus()
      val healthConnectData = healthConnectRepository.getAccountByID(accountId)
      val outOfSyncSession = healthConnectData?.outOfSync ?: false
      val isIntegrated = healthConnectData?.integrated ?: false
      when (healthConnectStatus) {
        HealthConnectStatus.INSTALLED,
        HealthConnectStatus.UPDATE_REQUIRED -> {
          val permissionStatus = checkPermissionStatus()

          // `healthConnectData != null` is implied by isIntegrated (== healthConnectData?.integrated)
          // but stated explicitly so the non-null value can be passed to the helper.
          if (outOfSyncSession && isIntegrated && healthConnectData != null) {
            return handleOutOfSyncPermissionStatus(accountId, healthConnectData, permissionStatus)
          }
        }

        HealthConnectStatus.UNAVAILABLE -> {
          AppLog.w(tag, "Health Connect unavailable")
          return false
        }

        else -> {
          AppLog.w(tag, "Health Connect status: $healthConnectStatus")
          return false
        }
      }
      false
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to check Health Connect out of sync status", e)
      false
    }
  }

  /**
   * Resolves the out-of-sync outcome for an installed Health Connect once a session is flagged
   * out-of-sync and integrated: NONE permissions → still out of sync (true); ALL/PARTIAL →
   * permissions restored, clear state + optionally prompt Finish Connect (false).
   */
  private suspend fun handleOutOfSyncPermissionStatus(
    accountId: String,
    healthConnectData: com.dmdbrands.gurus.weight.data.storage.datastore.HealthConnectData,
    permissionStatus: HealthConnectPermissionStatus,
  ): Boolean =
    when (permissionStatus) {
      HealthConnectPermissionStatus.NONE -> {
        // User has disabled permissions - mark as out of sync
        setOutOfSyncState(true)
        healthConnectRepository.updateOutOfSync(accountId, true)
        healthConnectRepository.updateModalState(accountId, true)
        AppLog.i(tag, "Health Connect permissions disabled - marked as out of sync")
        true
      }

      HealthConnectPermissionStatus.ALL,
      HealthConnectPermissionStatus.PARTIAL -> {
        setOutOfSyncState(false)
        healthConnectRepository.updateOutOfSync(accountId, false)
        val isModalDismissed = healthConnectData.modalState
        if (!isModalDismissed) {
          dialogQueueService.showDialog(
            DialogModel.Custom(
              contentKey = DialogType.FinishConnect,
              onConfirm = {
                appScope.launch {
                  appNavigationService.navigateTo(AppRoute.Integration.IntegrationList)
                  appNavigationService.navigateTo(AppRoute.Integration.HealthConnect)
                  dialogQueueService.dismissCurrent()
                }
              },
              onDismiss = {
                dialogQueueService.dismissCurrent()
              },
              dismissOnBackPress = true
            ),
          )
        }
        // Check for permission changes and update integration
        checkPermissionChange()
        AppLog.i(tag, "Health Connect permissions restored - integration updated")
        false // Not out of sync anymore
      }
    }

  /**
   * Checks Health Connect permission and shows the appropriate modal if needed.
   * This matches the Angular checkHealthConnectPermissionDisabled logic.
   */
  suspend fun checkHealthConnectPermissionDisabled() {
    AppLog.d(tag, "[checkHealthConnectPermissionDisabled] ENTRY")
    val healthConnectStatus = healthConnectStatus()
    AppLog.d(tag, "[checkHealthConnectPermissionDisabled] healthConnectStatus=$healthConnectStatus")
    val accountId = currentAccountId
    if (accountId == null) {
      AppLog.d(tag, "[checkHealthConnectPermissionDisabled] EARLY RETURN: accountId is null")
      return
    }
    AppLog.d(tag, "[checkHealthConnectPermissionDisabled] accountId=$accountId")
    val healthConnectData = healthConnectRepository.getAccountByID(accountId)
    AppLog.d(tag, "[checkHealthConnectPermissionDisabled] healthConnectData=$healthConnectData")
    val isHealthConnectOpened = healthConnectData?.open ?: false
    val outOfSyncSession = healthConnectData?.outOfSync ?: false
    val isIntegrationCancelled = healthConnectData?.alertSeen ?: false
    val isLocallyIntegrated = healthConnectData?.integrated ?: false
    AppLog.d(
      tag,
      "[checkHealthConnectPermissionDisabled] open=$isHealthConnectOpened outOfSync=$outOfSyncSession alertSeen=$isIntegrationCancelled integrated=$isLocallyIntegrated",
    )
    val integrationFromServer = integrationRepository.integrationsFromServer.first()
    if (healthConnectStatus === HealthConnectStatus.INSTALLED || healthConnectStatus === HealthConnectStatus.UPDATE_REQUIRED) {
      handleInstalledHealthConnect(
        accountId = accountId,
        isHealthConnectOpened = isHealthConnectOpened,
        outOfSyncSession = outOfSyncSession,
        isIntegrationCancelled = isIntegrationCancelled,
        isLocallyIntegrated = isLocallyIntegrated,
        integrationFromServer = integrationFromServer,
      )
    } else if (healthConnectStatus == HealthConnectStatus.INSTALL_REQUIRED && currentIntegrations?.isHealthConnectOn == true) {
      showHealthConnectNotAvailableAlert()
    } else {
      AppLog.d(tag, "[checkHealthConnectPermissionDisabled] branch: status=$healthConnectStatus (no INSTALLED/UPDATE_REQUIRED/INSTALL_REQUIRED match)")
    }
    AppLog.d(tag, "[checkHealthConnectPermissionDisabled] EXIT (normal end)")
  }

  /** Persists out-of-sync state and shows the OutOfSyncModal (remove / reconnect actions). */
  private suspend fun showOutOfSyncModal(accountId: String, shouldShowPopup: Boolean) {
    AppLog.d(tag, "[checkHealthConnectPermissionDisabled] ENTER: out-of-sync modal path (updateOutOfSync/ModalState, show OutOfSyncModal)")
    healthConnectRepository.updateOutOfSync(accountId, true)
    healthConnectRepository.updateModalState(accountId, true)
    setOutOfSyncState(true) // Set observable for out of sync
    if (shouldShowPopup) {
      AppLog.d(tag, "[checkHealthConnectPermissionDisabled] showing DialogType.OutOfSyncModal")
      dialogQueueService.showDialog(
        DialogModel.Custom(
          contentKey = DialogType.OutOfSyncModal,
          params =
            mapOf(
              "secondaryAction" to {
                appScope.launch {
                  dialogQueueService.showLoader(HealthConnectStrings.Loader.removing)
                  removeHealthConnectIntegration()
                  healthConnectRepository.updateOutOfSync(accountId, true)
                  healthConnectRepository.updateModalState(accountId, true)
                  dialogQueueService.dismissCurrent()
                  dialogQueueService.dismissLoader()
                }
              },
            ),
          onConfirm = {
            dialogQueueService.dismissCurrent()
            appScope.launch {
              openHealthConnect()
              healthConnectRepository.setHealthConnectIntegrationStatus(accountId, true)
              // On confirm, you may want to reset out-of-sync state if permissions are restored
              healthConnectRepository.updateOutOfSync(accountId, true)
              healthConnectRepository.updateModalState(accountId, true)
              setOutOfSyncState(true)
            }
          },
          onDismiss = {
            appScope.launch {
              healthConnectRepository.setHealthConnectIntegrationStatus(accountId, true)
              healthConnectRepository.updateOutOfSync(accountId, true)
              healthConnectRepository.updateModalState(accountId, true)
              setOutOfSyncState(true)
            }
          },
        ),
      )
      healthConnectRepository.updateModalState(accountId, true)
    }
    AppLog.d(tag, "[checkHealthConnectPermissionDisabled] RETURN: after out-of-sync modal path")
  }

  /**
   * Shows the FinishConnect prompt when permissions were granted: the hcOn=false case (fresh
   * connect) or the isHealthConnectOpened case (return from a failed integration attempt).
   */
  private suspend fun showFinishConnectModals(
    accountId: String,
    isHealthConnectOpened: Boolean,
    isIntegrationCancelled: Boolean,
    shouldShowPopup: Boolean,
  ) {
    val finishConnectCondition =
      currentIntegrations?.isHealthConnectOn == false && !isIntegrationCancelled && !isHealthConnectOpened
    AppLog.d(
      tag,
      "[checkHealthConnectPermissionDisabled] FinishConnect branch: hcOn=${currentIntegrations?.isHealthConnectOn} !alertSeen=${!isIntegrationCancelled} !opened=${!isHealthConnectOpened} -> showCondition=$finishConnectCondition",
    )
    if (finishConnectCondition) {
      if (shouldShowPopup) {
        AppLog.d(tag, "[checkHealthConnectPermissionDisabled] showing DialogType.FinishConnect (hcOn=false)")
        dialogQueueService.showDialog(
          DialogModel.Custom(
            contentKey = DialogType.FinishConnect,
            onConfirm = {
              appScope.launch {
                appNavigationService.navigateTo(AppRoute.Integration.IntegrationList)
                appNavigationService.navigateTo(AppRoute.Integration.HealthConnect)
                dialogQueueService.dismissCurrent()
              }
            },
            onDismiss = {
              appScope.launch {
                healthConnectRepository.updateAlertSeen(accountId, true)
                dialogQueueService.dismissCurrent()
              }
            },
          ),
        )
      }
      AppLog.d(tag, "[checkHealthConnectPermissionDisabled] RETURN: after FinishConnect (hcOn=false)")
      return
    } else if (isHealthConnectOpened) {
      AppLog.d(tag, "[checkHealthConnectPermissionDisabled] branch: isHealthConnectOpened=true (from integration failed), showing FinishConnect")
      // from integration failed
      if (shouldShowPopup) {
        dialogQueueService.showDialog(
          DialogModel.Custom(
            contentKey = DialogType.FinishConnect,
            onConfirm = {
              appScope.launch {
                healthConnectRepository.setOpen(accountId, false)
                appNavigationService.navigateTo(AppRoute.Integration.IntegrationList)
                appNavigationService.navigateTo(AppRoute.Integration.HealthConnect)
                dialogQueueService.dismissCurrent()
              }
            },
            onDismiss = {
              appScope.launch {
                healthConnectRepository.setOpen(accountId, false)
              }
              dialogQueueService.dismissCurrent()
            },
          ),
        )
        healthConnectRepository.updateModalState(accountId, false)
      }
      AppLog.d(tag, "[checkHealthConnectPermissionDisabled] RETURN: after FinishConnect (opened)")
    }
  }

  /** Enqueues the "Health Connect not available" alert (install required + integration on). */
  private fun showHealthConnectNotAvailableAlert() {
    AppLog.d(tag, "[checkHealthConnectPermissionDisabled] branch: INSTALL_REQUIRED + hcOn=true, enqueueing NotAvailable alert")
    dialogQueueService.enqueue(
      DialogModel.Alert(
        title = HealthConnectStrings.NotAvailable.header,
        message = HealthConnectStrings.NotAvailable.message,
        dismissText = HealthConnectStrings.ActionButtons.close,
        onDismiss = {
          dialogQueueService.dismissCurrent()
        },
      ),
    )
  }

  /**
   * Handles the INSTALLED / UPDATE_REQUIRED Health Connect branch: permission + connection checks,
   * out-of-sync flow, multi-device reconnect, and the out-of-sync / finish-connect modals.
   */
  @Suppress("LongParameterList")
  private suspend fun handleInstalledHealthConnect(
    accountId: String,
    isHealthConnectOpened: Boolean,
    outOfSyncSession: Boolean,
    isIntegrationCancelled: Boolean,
    isLocallyIntegrated: Boolean,
    integrationFromServer: Integrations?,
  ) {
    AppLog.d(tag, "[checkHealthConnectPermissionDisabled] branch: INSTALLED or UPDATE_REQUIRED")
    val permissionStatus = checkPermissionStatus()
    AppLog.d(tag, "[checkHealthConnectPermissionDisabled] permissionStatus=$permissionStatus")
    val isAlreadyConnected = try {
      checkIfAlreadyUsed()
    } catch (e: Exception) {
      AppLog.w(tag, "[checkHealthConnectPermissionDisabled] checkIfAlreadyUsed threw: ${e.message}")
      false
    }
    AppLog.d(tag, "[checkHealthConnectPermissionDisabled] isAlreadyConnected=$isAlreadyConnected")
    if (!isAlreadyConnected) {
      AppLog.d(tag, "[checkHealthConnectPermissionDisabled] RETURN: !isAlreadyConnected, setting _outOfSyncState=$outOfSyncSession")
      setOutOfSyncState(outOfSyncSession)
      return
    }
    val shouldShowPopup = true
    if (isLocallyIntegrated) {
      //for migration
      AppLog.d(tag, "[checkHealthConnectPermissionDisabled] isLocallyIntegrated=true, calling turnOnIntegration")
      turnOnIntegration(true, true)
    }
    // If permission is NONE and modal was opened, reset modal state
    if (permissionStatus == HealthConnectPermissionStatus.NONE && isHealthConnectOpened) {
      AppLog.d(tag, "[checkHealthConnectPermissionDisabled] NONE + opened -> updateModalState(accountId, false)")
      healthConnectRepository.updateModalState(accountId, false)
    }
    // Out of sync flow
    if (outOfSyncSession) {
      AppLog.d(tag, "[checkHealthConnectPermissionDisabled] outOfSyncSession=true, calling healthConnectOutOfSync()")
      healthConnectOutOfSync()
    }
    // If already connected, do not show popup
    // Multi-device connection check
    if (handleMultiDeviceConnection(permissionStatus, integrationFromServer, isLocallyIntegrated)) {
      return
    }
    // Out of sync modal
    val outOfSyncModalCondition =
      permissionStatus == HealthConnectPermissionStatus.NONE &&
        isLocallyIntegrated &&
        !outOfSyncSession
    AppLog.d(tag, "[checkHealthConnectPermissionDisabled] outOfSyncModalCondition - $outOfSyncModalCondition")
    if (outOfSyncModalCondition) {
      showOutOfSyncModal(accountId, shouldShowPopup)
      return
    }
    // If permissions are restored, clear out-of-sync state
    val permissionsRestoredCondition =
      (permissionStatus == HealthConnectPermissionStatus.ALL || permissionStatus == HealthConnectPermissionStatus.PARTIAL) && outOfSyncSession
    AppLog.d(tag, "[checkHealthConnectPermissionDisabled] permissionsRestoredCondition=$permissionsRestoredCondition")
    if (permissionsRestoredCondition) {
      AppLog.d(tag, "[checkHealthConnectPermissionDisabled] clearing out-of-sync state (permissions restored)")
      healthConnectRepository.updateOutOfSync(accountId, false)
      healthConnectRepository.updateModalState(accountId, false)
      // healthConnectRepository.updateOutOfSyncSession(accountId, false)
      setOutOfSyncState(false)
    }
    // Finish connect modal
    val hasPartialOrAllPermission =
      permissionStatus == HealthConnectPermissionStatus.PARTIAL ||
        permissionStatus == HealthConnectPermissionStatus.ALL
    AppLog.d(tag, "[checkHealthConnectPermissionDisabled] hasPartialOrAllPermission=$hasPartialOrAllPermission")
    if (hasPartialOrAllPermission) {
      showFinishConnectModals(accountId, isHealthConnectOpened, isIntegrationCancelled, shouldShowPopup)
    }
    AppLog.d(tag, "[checkHealthConnectPermissionDisabled] fall-through: no return in INSTALLED/UPDATE_REQUIRED branch")
  }

  /**
   * Runs the multi-device reconnect check when the server says HC is on but this device isn't
   * locally integrated. Returns true when a multi-device connection was handled (caller returns).
   */
  private suspend fun handleMultiDeviceConnection(
    permissionStatus: HealthConnectPermissionStatus,
    integrationFromServer: Integrations?,
    isLocallyIntegrated: Boolean,
  ): Boolean {
    val multiDeviceCondition =
      (permissionStatus == HealthConnectPermissionStatus.NONE ||
        permissionStatus == HealthConnectPermissionStatus.PARTIAL ||
        permissionStatus == HealthConnectPermissionStatus.ALL) &&
        integrationFromServer?.isHealthConnectOn == true &&
      !isLocallyIntegrated
    AppLog.d(tag, "[checkHealthConnectPermissionDisabled] multiDeviceCondition=$multiDeviceCondition (perm=$permissionStatus, hcOn=${integrationFromServer?.isHealthConnectOn}, !isLocallyIntegrated=${!isLocallyIntegrated})")
    if (multiDeviceCondition) {
      val isConnect = permissionStatus == HealthConnectPermissionStatus.PARTIAL ||
        permissionStatus == HealthConnectPermissionStatus.ALL
      AppLog.d(tag, "[checkHealthConnectPermissionDisabled] calling checkMultiDeviceConnection(isConnect=$isConnect)")
      val isMultiDeviceConnected = checkMultiDeviceConnection(isConnect)
      AppLog.d(tag, "[checkHealthConnectPermissionDisabled] isMultiDeviceConnected=$isMultiDeviceConnected")
      if (isMultiDeviceConnected) {
        AppLog.d(tag, "[checkHealthConnectPermissionDisabled] RETURN: isMultiDeviceConnected=true")
        return true
      }
    }
    return false
  }
}
