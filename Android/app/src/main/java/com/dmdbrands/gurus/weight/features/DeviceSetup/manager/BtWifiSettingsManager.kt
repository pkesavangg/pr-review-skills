package com.dmdbrands.gurus.weight.features.DeviceSetup.manager

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.model.api.device.R4ScalePreferenceApiModel
import com.dmdbrands.gurus.weight.domain.model.api.device.toR4ScalePreferenceApiModel
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.model.storage.Preferences
import com.dmdbrands.gurus.weight.domain.model.storage.toGGBTDevice
import com.dmdbrands.gurus.weight.domain.repository.IDeviceRepository
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.services.IDashboardService
import com.dmdbrands.gurus.weight.domain.services.IEntryReadService
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.BtWifiSetupStep
import com.dmdbrands.gurus.weight.features.DeviceSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.BtWifiScaleSetupIntent
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.BtWifiScaleSetupState
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.library.ggbluetooth.enums.GGUserActionResponseType
import com.greatergoods.blewrapper.GGDeviceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.TimeZone

/**
 * Owns the Customize / Update-Settings slice of [BtWifiScaleSetupViewModel] (MOB-1501).
 * Holds the [updateSettingsTimeoutJob] guarding the UPDATE_SETTINGS step.
 * Behaviour-preserving verbatim move.
 */
class BtWifiSettingsManager(
    private val ggDeviceService: GGDeviceService,
    private val deviceService: IDeviceService,
    private val deviceRepository: IDeviceRepository,
    private val dashboardService: IDashboardService,
    private val entryReadService: IEntryReadService,
    private val scope: CoroutineScope,
    private val operationTimeout: Long,
    private val getState: () -> BtWifiScaleSetupState,
    private val onIntent: (BtWifiScaleSetupIntent) -> Unit,
    private val getDiscoveredScale: () -> Device?,
    private val setDiscoveredScale: (Device?) -> Unit,
    private val getAccountId: () -> String?,
    private val onNext: () -> Unit,
) {

    private val TAG = "BtWifiSettingsManager"
    private var updateSettingsTimeoutJob: Job? = null

    fun updateDevicePreferences(dashboardKeys: List<DashboardKey>? = null, preferences: Preferences? = null) {
        scope.launch {
            try {
                updateSettingsTimeoutJob?.cancel()
                updateSettingsTimeoutJob = scope.launch {
                    delay(operationTimeout)
                    if (getState().currentStep == BtWifiSetupStep.UPDATE_SETTINGS) {
                        AppLog.w(TAG, "Update settings timeout reached")
                        setUpdateSettingsError()
                    }
                }
                if (dashboardKeys != null) {
                    dashboardService.updateVisibleKeys(
                        accountId = getAccountId(),
                        keys = dashboardKeys,
                        dashboardType = DashboardType.DASHBOARD_12_METRICS,
                    )
                }
                if (preferences != null) {
                    applyDevicePreferences(preferences)
                } else {
                    updateSettingsTimeoutJob?.cancel()
                    updateSettingsTimeoutJob = null
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Error during settings update", e)
                setUpdateSettingsError()
            }
        }
    }

    private suspend fun applyDevicePreferences(preferences: Preferences) {
        if (getDiscoveredScale()?.connectionStatus != BLEStatus.CONNECTED) {
            val bid = getDiscoveredScale()?.device?.broadcastId ?: getDiscoveredScale()?.device?.broadcastIdString
            if (bid != null) {
                val connected = withTimeoutOrNull(20_000L) {
                    ggDeviceService.deviceCache.first { cache ->
                        (cache[bid] as? Device)?.connectionStatus == BLEStatus.CONNECTED
                    }
                }
                if (connected == null) {
                    AppLog.w(TAG, "Scale did not reconnect within timeout before UPDATE_SETTINGS")
                    setUpdateSettingsError()
                    return
                }
            } else {
                setUpdateSettingsError()
                return
            }
        }
        val settingsScale = getDiscoveredScale() ?: run {
            AppLog.e(TAG, "discoveredScale is null during settings update")
            setUpdateSettingsError()
            return
        }
        val newName = getState().usernameForm.username.value
        val updatedDevice = settingsScale.copy(
            preferences = preferences.copy(
                displayName = newName.ifEmpty { preferences.displayName },
                id = settingsScale.id,
            ),
        )
        setDiscoveredScale(updatedDevice)
        ggDeviceService.updateAccount(updatedDevice.toGGBTDevice()) {
            onUpdateAccountResponse(it)
        }
        if (!getState().hasSavedSettings) {
            val scaleId = getDiscoveredScale()?.id ?: settingsScale.id
            updateScalePreferences(scaleId, preferences.toR4ScalePreferenceApiModel())
        }
    }

    private fun onUpdateAccountResponse(response: GGUserActionResponseType) {
        when (response) {
            GGUserActionResponseType.CREATION_COMPLETED, GGUserActionResponseType.UPDATE_COMPLETED -> {
                scope.launch {
                    updateSettingsTimeoutJob?.cancel()
                    updateSettingsTimeoutJob = null
                    val savedPrefs = getDiscoveredScale()?.preferences
                    if (savedPrefs != null) {
                        updateScalePreferences(getDiscoveredScale()?.id ?: "", savedPrefs.toR4ScalePreferenceApiModel())
                    }
                    AppLog.d(TAG, "Scale settings updated successfully")
                    onIntent(
                        BtWifiScaleSetupIntent.SetStepConnectionState(
                            BtWifiSetupStep.UPDATE_SETTINGS, ConnectionState.Success,
                        ),
                    )
                    getDiscoveredScale()?.let { ggDeviceService.syncDevices(listOf(it.toGGBTDevice())) }
                    onNext()
                }
            }
            else -> scope.launch { setUpdateSettingsError() }
        }
    }

    private suspend fun updateScalePreferences(deviceId: String, preferences: R4ScalePreferenceApiModel): Boolean {
        AppLog.d(TAG, "Updating scale preferences for device: $deviceId")
        return try {
            val updatedPreference = preferences.copy(
                wifiFotaScheduleTime = 0,
                tzOffset = getTimeZoneInMinutes(),
            )
            deviceRepository.saveScalePreferencesToApi(updatedPreference)
            deviceService.syncDevices()
            AppLog.d(TAG, "Scale preferences updated successfully")
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "Error updating scale preferences", e)
            false
        }
    }

    private fun getTimeZoneInMinutes(): Int {
        val timeZone = TimeZone.getDefault()
        return timeZone.getOffset(System.currentTimeMillis()) / (60 * 1000)
    }

    fun loadDashboardKeys() {
        scope.launch {
            dashboardService.getVisibleKeys().collect { dashboardKeys ->
                onIntent(BtWifiScaleSetupIntent.SetDashboardKeys(dashboardKeys))
            }
        }
    }

    fun loadGoalProgress() {
        scope.launch {
            entryReadService.weightProgress().collect {
                onIntent(BtWifiScaleSetupIntent.SetGoalProgress(it))
            }
        }
    }

    fun setUpdateSettingsError() {
        onIntent(
            BtWifiScaleSetupIntent.SetStepConnectionState(
                BtWifiSetupStep.UPDATE_SETTINGS, ConnectionState.Failed.Error,
            ),
        )
        cancelUpdateSettingsTimeout()
    }

    fun cancelUpdateSettingsTimeout() {
        updateSettingsTimeoutJob?.cancel()
        updateSettingsTimeoutJob = null
    }
}
