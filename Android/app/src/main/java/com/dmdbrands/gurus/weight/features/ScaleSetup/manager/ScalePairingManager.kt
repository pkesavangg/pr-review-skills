package com.dmdbrands.gurus.weight.features.ScaleSetup.manager

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.enums.MetricKeyConstants
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.model.storage.toGGBTDevice
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IDashboardService
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.BtWifiSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BtWifiScaleSetupIntent
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BtWifiScaleSetupIntent.SetCurrentStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BtWifiScaleSetupState
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.BtWifiScaleSetupStrings
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.ScaleSetupStrings
import com.dmdbrands.gurus.weight.features.ScaleUsers.strings.ScaleUsersStrings
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.gurus.weight.features.common.helper.StatHelper
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.library.ggbluetooth.enums.GGUserActionResponseType
import com.dmdbrands.library.ggbluetooth.model.GGBTDevice
import com.dmdbrands.library.ggbluetooth.model.GGBTUser
import com.greatergoods.blewrapper.GGDeviceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class ScalePairingManager(
    private val ggDeviceService: GGDeviceService,
    private val deviceService: IDeviceService,
    private val accountService: IAccountService,
    private val dashboardService: IDashboardService,
    private val sku: String,
    private val scope: CoroutineScope,
    private val operationTimeout: Long,
    private val getState: () -> BtWifiScaleSetupState,
    private val onIntent: (BtWifiScaleSetupIntent) -> Unit,
    private val getDiscoveredScale: () -> Device?,
    private val setDiscoveredScale: (Device?) -> Unit,
    private val setIsScaleConnected: (Boolean) -> Unit,
    private val getAccountId: () -> String?,
    private val onNext: () -> Unit,
    private val enqueueDialog: (DialogModel) -> Unit,
) : IScalePairingManager {

    private val TAG = "ScalePairingManager"
    private var bluetoothConnectionTimeoutJob: Job? = null

    override fun connectToBluetooth() {
        onIntent(
            BtWifiScaleSetupIntent.SetStepConnectionState(
                BtWifiSetupStep.CONNECTING_BLUETOOTH,
                ConnectionState.Loading,
            ),
        )
        scope.launch {
            try {
                bluetoothConnectionTimeoutJob = scope.launch {
                    delay(operationTimeout)
                    if (getState().currentStep == BtWifiSetupStep.CONNECTING_BLUETOOTH) {
                        AppLog.w(TAG, "Bluetooth connection timeout reached")
                        onIntent(
                            BtWifiScaleSetupIntent.SetStepConnectionState(
                                BtWifiSetupStep.CONNECTING_BLUETOOTH,
                                ConnectionState.Failed.Error,
                            ),
                        )
                    }
                }
                val ggBtDevice = getDiscoveredScale()?.toGGBTDevice() ?: run {
                    AppLog.e(TAG, "discoveredScale is null when connecting to Bluetooth")
                    setBluetoothFailedStatus()
                    return@launch
                }
                ggDeviceService.pairDevice(device = ggBtDevice) { response ->
                    bluetoothConnectionTimeoutJob?.cancel()
                    bluetoothConnectionTimeoutJob = null
                    when (response) {
                        GGUserActionResponseType.CREATION_COMPLETED -> {
                            scope.launch {
                                onIntent(BtWifiScaleSetupIntent.SetScaleId(getDiscoveredScale()?.id ?: ""))
                                onIntent(
                                    BtWifiScaleSetupIntent.SetStepConnectionState(
                                        BtWifiSetupStep.CONNECTING_BLUETOOTH,
                                        ConnectionState.Success,
                                    ),
                                )
                                val currentTime = Instant.now().toString()
                                val pairedScale = getDiscoveredScale() ?: return@launch
                                val updatedScale = pairedScale.copy(
                                    connectionStatus = BLEStatus.CONNECTED,
                                    deviceType = ScaleSetupType.BtWifiR4.value,
                                    sku = sku,
                                    createdAt = currentTime,
                                )
                                setDiscoveredScale(updatedScale)
                                setDiscoveredScale(deviceService.saveScale(requireNotNull(getDiscoveredScale()) { "discoveredScale unexpectedly null after copy" }))
                                setIsScaleConnected(true)
                                try {
                                    fetchUserList()
                                    val activeAccount = accountService.activeAccountFlow.first()
                                    if (activeAccount?.dashboardType == DashboardType.DASHBOARD_4_METRICS.value) {
                                        accountService.updateDashboardType(DashboardType.DASHBOARD_12_METRICS)
                                        val dashboardMetrics = activeAccount.dashboardMetrics ?: emptyList()
                                        val additionalMetrics = StatHelper.getAdditionalMetrics()
                                        val updatedMetrics = dashboardMetrics.toMutableList().apply {
                                            additionalMetrics.forEach { metric ->
                                                if (!contains(metric)) add(metric)
                                            }
                                        }
                                        val metricKeys = updatedMetrics.mapNotNull { MetricKeyConstants.CAMEL_CASE_TO_ENUM[it] }
                                        dashboardService.updateVisibleMetricKeys(getAccountId(), metricKeys, DashboardType.DASHBOARD_12_METRICS)
                                    }
                                } catch (e: Exception) {
                                    AppLog.e(TAG, "Error in background operations (user list fetch or dashboard update)", e)
                                }
                                onNext()
                            }
                        }

                        GGUserActionResponseType.DUPLICATE_USER_ERROR -> {
                            scope.launch {
                                fetchUserList()
                                val duplicateUserName = getDiscoveredScale()?.preferences?.displayName
                                    ?: getState().usernameForm.username.value.takeIf { it.isNotEmpty() }
                                    ?: accountService.activeAccountFlow.first()?.firstName?.take(20)
                                AppLog.d(TAG, "Found duplicate user: $duplicateUserName")
                                checkDuplicateUserList()
                                if (duplicateUserName != null) {
                                    onIntent(SetCurrentStep(BtWifiSetupStep.DUPLICATES_FOUND))
                                } else {
                                    AppLog.e(TAG, "Could not determine duplicate username")
                                    setBluetoothFailedStatus()
                                }
                            }
                        }

                        GGUserActionResponseType.MEMORY_FULL -> {
                            scope.launch {
                                fetchUserList(
                                    onSuccess = {
                                        onIntent(SetCurrentStep(BtWifiSetupStep.USER_LIMIT_REACHED))
                                    },
                                )
                            }
                        }

                        else -> setBluetoothFailedStatus()
                    }
                }
            } catch (e: Exception) {
                bluetoothConnectionTimeoutJob?.cancel()
                bluetoothConnectionTimeoutJob = null
                AppLog.e(TAG, "Error during bluetooth connection", e)
                onIntent(
                    BtWifiScaleSetupIntent.SetStepConnectionState(
                        BtWifiSetupStep.CONNECTING_BLUETOOTH,
                        ConnectionState.Failed.Error,
                    ),
                )
            }
        }
    }

    override fun replaceAccount(userName: String?) {
        try {
            scope.launch {
                val scale = getDiscoveredScale() ?: return@launch
                setDiscoveredScale(
                    scale.copy(preferences = scale.preferences?.copy(displayName = userName)),
                )
                onIntent(BtWifiScaleSetupIntent.UpdateNextButtonText(ScaleSetupStrings.SetupButtons.Next))
                onIntent(SetCurrentStep(BtWifiSetupStep.CONNECTING_BLUETOOTH))
            }
        } catch (e: Exception) {
            AppLog.d(TAG, "Error replacing account")
        }
    }

    override fun deleteUser(user: GGBTUser) {
        try {
            enqueueDialog(
                DialogModel.Confirm(
                    title = ScaleUsersStrings.DeleteUserAlert.Title,
                    message = ScaleUsersStrings.DeleteUserAlert.Message(user.name),
                    confirmText = ScaleUsersStrings.DeleteUserAlert.Delete,
                    cancelText = ScaleUsersStrings.DeleteUserAlert.Back,
                    primaryActionType = ButtonType.ErrorText,
                    onConfirm = {
                        scope.launch {
                            val deleteDevice = getDiscoveredScale()?.copy(
                                preferences = getDiscoveredScale()?.preferences?.copy(
                                    displayName = user.name,
                                    shouldMeasureImpedance = user.isBodyMetricsEnabled,
                                ),
                                token = user.token,
                            ) ?: return@launch
                            ggDeviceService.deleteAccount(deleteDevice.toGGBTDevice()) {}
                            restartConnection()
                        }
                    },
                ),
            )
        } catch (e: Exception) {
            AppLog.e(TAG, "Error during user deletion", e)
        }
    }

    override fun showRestoreAccountAlert() {
        enqueueDialog(
            DialogModel.Confirm(
                title = BtWifiScaleSetupStrings.DuplicateUser.RestoreConfirmation.Title,
                message = BtWifiScaleSetupStrings.DuplicateUser.RestoreConfirmation.Message,
                confirmText = BtWifiScaleSetupStrings.DuplicateUser.RestoreConfirmation.Restore,
                cancelText = BtWifiScaleSetupStrings.DuplicateUser.RestoreConfirmation.Back,
                onConfirm = {
                    scope.launch { deleteUsers() }
                },
            ),
        )
    }

    override fun cancelTimeout() {
        bluetoothConnectionTimeoutJob?.cancel()
        bluetoothConnectionTimeoutJob = null
    }

    private suspend fun fetchUserList(duplicateUserName: String? = null, onSuccess: (() -> Unit)? = null) {
        try {
            val scale = getDiscoveredScale() ?: return
            val userList = suspendCoroutine { continuation ->
                ggDeviceService.getUsers(scale.toGGBTDevice()) { response ->
                    if (duplicateUserName != null) {
                        val user = response.user.first { it.name == duplicateUserName }
                        onIntent(BtWifiScaleSetupIntent.SetDuplicateUser(user))
                    }
                    continuation.resume(response.user)
                    onSuccess?.invoke()
                }
            }
            val filteredUserList = userList.filter { user -> user.token != getDiscoveredScale()?.token }
            val currentUserName = accountService.activeAccountFlow.first()?.firstName?.take(20)
            val listWithActiveUserOnTop = if (currentUserName != null) {
                filteredUserList.sortedByDescending { user ->
                    user.name.equals(currentUserName, ignoreCase = true)
                }
            } else {
                AppLog.w(TAG, "No active user name available for sorting user list")
                filteredUserList
            }
            AppLog.d(TAG, "During fetching user list $userList")
            onIntent(BtWifiScaleSetupIntent.SetUserList(listWithActiveUserOnTop))
        } catch (e: Exception) {
            AppLog.e(TAG, "Error during fetching user list", e)
            onIntent(
                BtWifiScaleSetupIntent.SetStepConnectionState(
                    BtWifiSetupStep.CONNECTING_BLUETOOTH,
                    ConnectionState.Failed.Error,
                ),
            )
        }
    }

    private suspend fun checkDuplicateUserList() {
        try {
            val currentUserName = accountService.activeAccountFlow.first()?.firstName?.take(20)
            val currentUser = getState().userList.find { user ->
                user.name.equals(currentUserName, ignoreCase = true)
            }
            AppLog.e(TAG, "Error checking duplicate user list $currentUser")
            AppLog.e(TAG, "Error checking duplicate user list ${getState().userList}")
            onIntent(BtWifiScaleSetupIntent.SetDuplicateUser(currentUser))
            if (currentUser != null) {
                val duplicateList = getState().userList.filter { user ->
                    user.name.equals(currentUser.name, ignoreCase = true)
                }
                onIntent(BtWifiScaleSetupIntent.SetDuplicateUserList(duplicateList))
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Error checking duplicate user list", e)
        }
    }

    private fun deleteUsers() {
        scope.launch {
            try {
                val broadcastId = getDiscoveredScale()?.device?.broadcastId
                if (broadcastId == null) {
                    AppLog.e(TAG, "Cannot delete users: broadcastId is null")
                    return@launch
                }
                for (user in getState().duplicateUserList) {
                    deleteUserByBroadcastIdAndToken(broadcastId, user.token)
                }
                restartConnection()
            } catch (e: Exception) {
                AppLog.e(TAG, "Error deleting users", e)
            }
        }
    }

    private fun deleteUserByBroadcastIdAndToken(broadcastId: String, token: String) {
        try {
            val minimalDevice = GGBTDevice(
                name = "",
                broadcastId = broadcastId,
                token = token,
            )
            ggDeviceService.deleteAccount(minimalDevice) {}
        } catch (e: Exception) {
            AppLog.e(TAG, "Error deleting user with token: $token", e)
        }
    }

    private fun restartConnection() {
        scope.launch {
            delay(1000)
            onIntent(BtWifiScaleSetupIntent.SetDuplicateUser(null))
            onIntent(BtWifiScaleSetupIntent.SetDuplicateUserList(emptyList()))
            onIntent(BtWifiScaleSetupIntent.UpdateNextButtonText(ScaleSetupStrings.SetupButtons.Next))
            onIntent(SetCurrentStep(BtWifiSetupStep.CONNECTING_BLUETOOTH))
        }
    }

    private fun setBluetoothFailedStatus() {
        onIntent(
            BtWifiScaleSetupIntent.SetStepConnectionState(
                BtWifiSetupStep.CONNECTING_BLUETOOTH,
                ConnectionState.Failed.Error,
            ),
        )
    }
}
