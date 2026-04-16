package com.dmdbrands.gurus.weight.features.ScaleSetup.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.config.AppConfig
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.network.utility.NetworkState
import com.dmdbrands.gurus.weight.core.service.BluetoothPreferencesService
import com.dmdbrands.gurus.weight.core.shared.utilities.NameUtils
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.model.api.device.R4ScalePreferenceApiModel
import com.dmdbrands.gurus.weight.domain.model.api.device.toR4ScalePreferenceApiModel
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.model.storage.Preferences
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.model.storage.toGGBTDevice
import com.dmdbrands.gurus.weight.domain.repository.IDeviceRepository
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IDashboardService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IEntryReadService
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.BtWifiSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.manager.BLEDiscoveryManager
import com.dmdbrands.gurus.weight.features.ScaleSetup.manager.IBLEDiscoveryManager
import com.dmdbrands.gurus.weight.features.ScaleSetup.manager.IScalePairingManager
import com.dmdbrands.gurus.weight.features.ScaleSetup.manager.IWiFiConfigManager
import com.dmdbrands.gurus.weight.features.ScaleSetup.manager.ScalePairingManager
import com.dmdbrands.gurus.weight.features.ScaleSetup.manager.WiFiConfigManager
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BtWifiScaleSetupIntent
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BtWifiScaleSetupIntent.SetCurrentStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BtWifiScaleSetupReducer
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BtWifiScaleSetupState
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.ScaleSetupStrings
import com.dmdbrands.gurus.weight.features.appPermissions.helper.AppPermissionsHelper
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionState
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionType
import com.dmdbrands.library.ggbluetooth.enums.GGScanResponseType
import com.dmdbrands.library.ggbluetooth.enums.GGUserActionResponseType
import com.dmdbrands.library.ggbluetooth.model.GGBTUser
import com.dmdbrands.library.ggbluetooth.model.GGLiveDataResponse
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.dmdbrands.library.ggbluetooth.model.GGScanResponse
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.blewrapper.GGPermissionService
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.util.TimeZone

/**
 * Coordinator ViewModel for the BtWifiScaleSetupScreen.
 * Delegates BLE discovery, WiFi config, and scale pairing to focused manager classes.
 */
@HiltViewModel(assistedFactory = BtWifiScaleSetupViewModel.Factory::class)
class BtWifiScaleSetupViewModel @AssistedInject constructor(
    @Assisted("sku") private val sku: String,
    @Assisted("broadcastId") private val broadcastId: String? = null,
    @Assisted("initialStep") private val initialStep: BtWifiSetupStep = BtWifiSetupStep.SCALE_INFO,
    @Assisted("userList") private val userList: List<GGBTUser>? = null,
    override val ggDeviceService: GGDeviceService,
    private val deviceService: IDeviceService,
    private val entryService: IEntryService,
    private val entryReadService: IEntryReadService,
    private val deviceRepository: IDeviceRepository,
    private val dashboardService: IDashboardService,
    override val permissionService: GGPermissionService,
    override val connectivityObserver: IConnectivityObserver,
    private val dialogUtility: IDialogUtility,
    private val accountService: IAccountService,
    private val bluetoothPreferencesService: BluetoothPreferencesService,
) : ScaleSetupViewmodel<BtWifiScaleSetupState, BtWifiScaleSetupIntent>(
    ggDeviceService, connectivityObserver, permissionService,
    reducer = BtWifiScaleSetupReducer(),
) {
    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("sku") sku: String,
            @Assisted("broadcastId") broadcastId: String? = null,
            @Assisted("initialStep") initialStep: BtWifiSetupStep = BtWifiSetupStep.SCALE_INFO,
            @Assisted("userList") userList: List<GGBTUser>? = null,
        ): BtWifiScaleSetupViewModel
    }

    private val TAG = "BtWifiScaleSetupViewModel"

    // Timeout constants
    private val operationTimeout: Long = 5 * 60 * 1000L
    private val permissionCheckTimeOut: Long = 5 * 1000
    private val connectionDelay: Long = 2000L

    private var isScaleConnected: Boolean = discoveredScale?.connectionStatus == BLEStatus.CONNECTED
    private var accountId: String? = null
    private var updateSettingsTimeoutJob: kotlinx.coroutines.Job? = null
    private var measurementTimeoutJob: kotlinx.coroutines.Job? = null

    // Managers — ViewModel creates and owns them, passing itself as the dependency source
    private val bleManager: IBLEDiscoveryManager = BLEDiscoveryManager(
        ggDeviceService = ggDeviceService,
        deviceService = deviceService,
        accountService = accountService,
        bluetoothPreferencesService = bluetoothPreferencesService,
        sku = sku,
        scope = viewModelScope,
        operationTimeout = operationTimeout,
        connectionDelay = connectionDelay,
        getState = { state.value },
        onIntent = ::handleIntent,
        getDiscoveredScale = { discoveredScale },
        setDiscoveredScale = { discoveredScale = it },
        setIsScaleConnected = { isScaleConnected = it },
        onNext = ::onNext,
        onExitSetup = ::onExitSetup,
        startObservingDevices = ::startObservingDevices,
        stopObservingDevices = ::stopObservingDevices,
        showDialog = { dialogQueueService.showDialog(it) },
        dismissCurrentDialog = { dialogQueueService.dismissCurrent() },
        setModePreference = ::setModePreference,
    )

    private val wifiManager: IWiFiConfigManager = WiFiConfigManager(
        ggDeviceService = ggDeviceService,
        scope = viewModelScope,
        initialStep = initialStep,
        operationTimeout = operationTimeout,
        connectionDelay = connectionDelay,
        getState = { state.value },
        onIntent = ::handleIntent,
        getDiscoveredScale = { discoveredScale },
        onNext = ::onNext,
        onExitSetup = ::onExitSetup,
        requestNotificationPermission = { requestPermission(GGPermissionType.NOTIFICATION) },
    )

    private val scalePairingManager: IScalePairingManager = ScalePairingManager(
        ggDeviceService = ggDeviceService,
        deviceService = deviceService,
        accountService = accountService,
        dashboardService = dashboardService,
        sku = sku,
        scope = viewModelScope,
        operationTimeout = operationTimeout,
        getState = { state.value },
        onIntent = ::handleIntent,
        getDiscoveredScale = { discoveredScale },
        setDiscoveredScale = { discoveredScale = it },
        setIsScaleConnected = { isScaleConnected = it },
        getAccountId = { accountId },
        onNext = ::onNext,
        enqueueDialog = { dialogQueueService.enqueue(it) },
    )

    init {
        deviceService.setSetupInProgress(true)
        loadScaleInfo()
        initializePermissionsImmediately()
        observePermissions()
        observeStepChanges()
        initializeSetup()
        subscribeLatestWeight()
        fallbackToErrorsIfPermissionIsDisabled()
        viewModelScope.launch {
            accountId = accountService.activeAccountFlow.first()?.id
        }
    }

    override fun provideInitialState(): BtWifiScaleSetupState = BtWifiScaleSetupState()

    // --- Intent routing ---

    override fun handleIntent(intent: BtWifiScaleSetupIntent) {
        when (intent) {
            is BtWifiScaleSetupIntent.ReplaceAccount -> scalePairingManager.replaceAccount(intent.userName)
            BtWifiScaleSetupIntent.ShowRestoreAccountAlert -> scalePairingManager.showRestoreAccountAlert()
            BtWifiScaleSetupIntent.Next -> onNext()
            BtWifiScaleSetupIntent.Back -> onBack()
            BtWifiScaleSetupIntent.Skip -> onSkip()
            BtWifiScaleSetupIntent.TryAgain -> onTryAgain()
            is BtWifiScaleSetupIntent.UpdateSettings -> updateDevicePreferences(intent.dashboardKeys, intent.preferences)
            BtWifiScaleSetupIntent.RefreshNetworks -> onRefreshNetworks()
            BtWifiScaleSetupIntent.HandlePasswordNetworkStatus -> wifiManager.handlePasswordNetworkStatus()
            is BtWifiScaleSetupIntent.RequestPermission -> requestPermission(intent.permissionType)
            is BtWifiScaleSetupIntent.ExitSetup -> onExitSetup(intent.isSetupFinished)
            BtWifiScaleSetupIntent.OpenHelp -> openHelpModal()
            BtWifiScaleSetupIntent.OpenAccucheckModal -> openAccucheckModel()
            BtWifiScaleSetupIntent.OpenBiaModal -> openBiaModel()
            is BtWifiScaleSetupIntent.DeleteUser -> scalePairingManager.deleteUser(intent.user)
            BtWifiScaleSetupIntent.ClearWifiPasswordForm -> wifiManager.clearWifiPasswordForm()
            else -> {}
        }
        super.handleIntent(intent)
    }

    // --- Base class overrides (thin delegation) ---

    override fun onScanResponse(response: GGScanResponse.DeviceDetail) {
        bleManager.handleScanResponse(response)
    }

    override fun onEntryResponse(response: GGScanResponse.Entry) {
        when (response.type) {
            GGScanResponseType.SINGLE_ENTRY -> {
                measurementTimeoutJob?.cancel()
                measurementTimeoutJob = null
                viewModelScope.launch {
                    handleIntent(
                        BtWifiScaleSetupIntent.SetStepConnectionState(
                            BtWifiSetupStep.MEASUREMENT,
                            ConnectionState.Success,
                        ),
                    )
                    onNext()
                }
            }
            else -> Unit
        }
    }

    // --- Setup flow: step observation and navigation ---

    private fun observeStepChanges() {
        viewModelScope.launch {
            var previousStep: BtWifiSetupStep? = null
            state.collect { currentState ->
                val currentStep = currentState.currentStep
                if (previousStep != null && previousStep != currentStep) {
                    when (currentStep) {
                        BtWifiSetupStep.WAKEUP -> bleManager.startPairing()
                        BtWifiSetupStep.CONNECTING_BLUETOOTH -> scalePairingManager.connectToBluetooth()
                        BtWifiSetupStep.DUPLICATES_FOUND ->
                            handleIntent(BtWifiScaleSetupIntent.UpdateNextButtonText(ScaleSetupStrings.SetupButtons.Save))
                        BtWifiSetupStep.GATHERING_NETWORK -> {
                            if (!AppPermissionsHelper.areRequiredPermissionsEnabled(
                                    state.value.permissions, setupType = ScaleSetupType.BtWifiR4,
                                )
                            ) {
                                setGatheringNetworkFailed()
                            } else {
                                wifiManager.gatherNetworks()
                            }
                        }
                        BtWifiSetupStep.WIFI_PASSWORD ->
                            handleIntent(BtWifiScaleSetupIntent.UpdateNextButtonText(ScaleSetupStrings.SetupButtons.Connect))
                        BtWifiSetupStep.CONNECTING_WIFI -> wifiManager.connectToWifi()
                        BtWifiSetupStep.CUSTOMIZE_SETTINGS -> {
                            loadDashboardKeys()
                            loadGoalProgress()
                        }
                        BtWifiSetupStep.UPDATE_SETTINGS -> {
                            val currentUpdateSettingsState = currentState.stepConnectionStates[BtWifiSetupStep.UPDATE_SETTINGS]
                            if (currentUpdateSettingsState !is ConnectionState.Success) {
                                handleIntent(
                                    BtWifiScaleSetupIntent.SetStepConnectionState(
                                        BtWifiSetupStep.UPDATE_SETTINGS,
                                        ConnectionState.Loading,
                                    ),
                                )
                            }
                        }
                        BtWifiSetupStep.STEP_ON -> {
                            if (!AppPermissionsHelper.areRequiredPermissionsEnabled(
                                    state.value.permissions, setupType = ScaleSetupType.BtWifiR4,
                                )
                            ) {
                                val disabledPermissions = AppPermissionsHelper.getDisabledPermissionsForSetupType(
                                    permissionMap = state.value.permissions, setupType = ScaleSetupType.BtWifiR4,
                                )
                                val isOnlyNetworkPermissionMissing = disabledPermissions.size == 1 &&
                                    disabledPermissions.contains(GGPermissionType.WIFI_SWITCH)
                                if (!isOnlyNetworkPermissionMissing) setMeasurementFailed() else stepOn()
                            } else {
                                stepOn()
                            }
                        }
                        BtWifiSetupStep.MEASUREMENT -> {
                            val measurementConnectionState = currentState.stepConnectionStates[BtWifiSetupStep.MEASUREMENT]
                            if (measurementConnectionState !is ConnectionState.Failed) {
                                collectMeasurement()
                            }
                        }
                        else -> {}
                    }
                }
                previousStep = currentStep
            }
        }
    }

    private fun onNext() {
        val currentState = state.value
        if (currentState.isLastStep) handleIntent(BtWifiScaleSetupIntent.ExitSetup(true))
        when (currentState.currentStep) {
            BtWifiSetupStep.SCALE_INFO -> {
                viewModelScope.launch {
                    val arePermissionsEnabled = AppPermissionsHelper.areRequiredPermissionsEnabled(
                        currentState.permissions, setupType = ScaleSetupType.BtWifiR4,
                    )
                    if (arePermissionsEnabled) {
                        handleIntent(SetCurrentStep(BtWifiSetupStep.WAKEUP))
                    } else {
                        permissionAccess()
                        handleIntent(SetCurrentStep(BtWifiSetupStep.PERMISSIONS))
                    }
                }
                return
            }
            BtWifiSetupStep.PERMISSIONS -> { handleIntent(SetCurrentStep(BtWifiSetupStep.WAKEUP)); return }
            BtWifiSetupStep.WAKEUP -> { handleIntent(SetCurrentStep(BtWifiSetupStep.CONNECTING_BLUETOOTH)); return }
            BtWifiSetupStep.CONNECTING_BLUETOOTH -> { handleIntent(SetCurrentStep(BtWifiSetupStep.GATHERING_NETWORK)); return }
            BtWifiSetupStep.GATHERING_NETWORK -> { handleIntent(SetCurrentStep(BtWifiSetupStep.AVAILABLE_WIFI_LIST)); return }
            BtWifiSetupStep.DUPLICATES_FOUND -> {
                val newUserName = _state.value.usernameForm.username.value
                if (newUserName != _state.value.duplicateUser?.name) {
                    handleIntent(BtWifiScaleSetupIntent.ReplaceAccount(newUserName))
                }
            }
            BtWifiSetupStep.WIFI_PASSWORD -> { handleIntent(SetCurrentStep(BtWifiSetupStep.CONNECTING_WIFI)); return }
            BtWifiSetupStep.CONNECTING_WIFI -> { handleIntent(SetCurrentStep(BtWifiSetupStep.CUSTOMIZE_SETTINGS)); return }
            BtWifiSetupStep.AVAILABLE_WIFI_LIST -> {
                if (!currentState.connectedSSID.isNullOrEmpty()) {
                    discoveredScale?.let { ggDeviceService.cancelWifi(it.toGGBTDevice()) {} }
                        ?: AppLog.w(TAG, "discoveredScale is null when canceling WiFi from available WiFi list")
                    handleIntent(SetCurrentStep(BtWifiSetupStep.CUSTOMIZE_SETTINGS))
                }
                return
            }
            BtWifiSetupStep.CUSTOMIZE_SETTINGS -> {
                if (hasCustomizationChanges()) handleIntent(SetCurrentStep(BtWifiSetupStep.UPDATE_SETTINGS))
                else handleIntent(SetCurrentStep(BtWifiSetupStep.STEP_ON))
                return
            }
            BtWifiSetupStep.UPDATE_SETTINGS -> { handleIntent(SetCurrentStep(BtWifiSetupStep.STEP_ON)); return }
            BtWifiSetupStep.STEP_ON -> { handleIntent(SetCurrentStep(BtWifiSetupStep.MEASUREMENT)); return }
            BtWifiSetupStep.MEASUREMENT -> { handleIntent(SetCurrentStep(BtWifiSetupStep.SETUP_FINISHED)); return }
            else -> {}
        }
    }

    private fun onBack() {
        val currentState = state.value
        AppLog.d(TAG, "Moving to previous step from: ${currentState.currentStep}")
        when (currentState.currentStep) {
            BtWifiSetupStep.WAKEUP -> ggDeviceService.resumeScan(true)
            BtWifiSetupStep.CUSTOMIZE_SETTINGS -> handleIntent(SetCurrentStep(BtWifiSetupStep.GATHERING_NETWORK))
            BtWifiSetupStep.WIFI_PASSWORD -> handleIntent(SetCurrentStep(BtWifiSetupStep.AVAILABLE_WIFI_LIST))
            else -> {
                val nextIndex = currentState.currentStepIndex - 1
                if (nextIndex < currentState.steps.size) {
                    handleIntent(SetCurrentStep(currentState.steps[nextIndex]))
                }
            }
        }
    }

    private fun onSkip() {
        val currentState = state.value
        when (currentState.currentStep) {
            BtWifiSetupStep.GATHERING_NETWORK,
            BtWifiSetupStep.AVAILABLE_WIFI_LIST -> showWifiSkipConfirmation()
            else -> onNext()
        }
    }

    private fun showWifiSkipConfirmation() {
        dialogQueueService.enqueue(
            DialogModel.Confirm(
                title = ScaleSetupStrings.SkipBtWifiPermissions.Title,
                message = ScaleSetupStrings.SkipBtWifiPermissions.Message,
                confirmText = ScaleSetupStrings.SkipBtWifiPermissions.Skip,
                cancelText = ScaleSetupStrings.SkipBtWifiPermissions.Back,
                onConfirm = {
                    discoveredScale?.let { ggDeviceService.cancelWifi(it.toGGBTDevice()) {} }
                        ?: AppLog.w(TAG, "discoveredScale is null when skipping WiFi, skipping cancelWifi call")
                    handleIntent(SetCurrentStep(BtWifiSetupStep.CUSTOMIZE_SETTINGS))
                },
                onCancel = {},
            ),
        )
    }

    private fun onTryAgain() {
        val currentState = state.value
        when (currentState.currentStep) {
            BtWifiSetupStep.WAKEUP -> {
                bleManager.cancelPairing()
                ggDeviceService.resumeScan(true)
                bleManager.startPairing()
            }
            BtWifiSetupStep.CONNECTING_BLUETOOTH -> {
                scalePairingManager.cancelTimeout()
                val currentScale = discoveredScale
                if (currentScale != null) {
                    currentScale.device?.broadcastId?.let { ggDeviceService.disconnectDevice(currentScale.toGGBTDevice()) }
                    scalePairingManager.connectToBluetooth()
                } else {
                    ggDeviceService.resumeScan(true)
                    bleManager.startPairing()
                }
            }
            BtWifiSetupStep.GATHERING_NETWORK -> wifiManager.gatherNetworks()
            BtWifiSetupStep.CONNECTING_WIFI -> {
                wifiManager.cancelTimeout()
                handleIntent(SetCurrentStep(BtWifiSetupStep.GATHERING_NETWORK))
                wifiManager.gatherNetworks()
            }
            BtWifiSetupStep.STEP_ON -> stepOn()
            BtWifiSetupStep.UPDATE_SETTINGS -> {
                updateSettingsTimeoutJob?.cancel()
                updateSettingsTimeoutJob = null
                goToCustomiseSettings()
            }
            BtWifiSetupStep.MEASUREMENT -> {
                measurementTimeoutJob?.cancel()
                measurementTimeoutJob = null
                collectMeasurement()
            }
            else -> AppLog.w(TAG, "Try again called on step that doesn't support retry: ${currentState.currentStep}")
        }
    }

    fun goToCustomiseSettings() {
        handleIntent(SetCurrentStep(BtWifiSetupStep.CUSTOMIZE_SETTINGS))
    }

    private fun onRefreshNetworks() {
        handleIntent(SetCurrentStep(BtWifiSetupStep.GATHERING_NETWORK))
    }

    // --- Exit ---

    private fun onExitSetup(isSetupFinished: Boolean) {
        deviceService.setSetupInProgress(false)
        if (isSetupFinished) {
            onExit()
        } else {
            dialogQueueService.enqueue(
                DialogModel.Confirm(
                    title = ScaleSetupStrings.ExitSetupAlert.Title,
                    message = ScaleSetupStrings.ExitSetupAlert.Message(discoveredScale?.connectionStatus == BLEStatus.CONNECTED),
                    confirmText = ScaleSetupStrings.ExitSetupAlert.Exit,
                    cancelText = ScaleSetupStrings.ExitSetupAlert.GoBack,
                    onConfirm = { onExit() },
                ),
            )
        }
    }

    private fun onExit() {
        clearAllTimeouts()
        viewModelScope.launch {
            try {
                ggDeviceService.resumeScan(false)
                discoveredScale?.let { scale ->
                    ggDeviceService.cancelWifi(scale.toGGBTDevice()) {}
                    if (!isScaleConnected && initialStep != BtWifiSetupStep.GATHERING_NETWORK) {
                        ggDeviceService.deleteAccount(scale.toGGBTDevice(), true) {}
                        ggDeviceService.disconnectDevice(scale.toGGBTDevice())
                    }
                }
                loadPluginData()
            } catch (e: Exception) {
                AppLog.e(TAG, "Error during Bluetooth cleanup", e)
            }
            try {
                navigationService.navigateBack()
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to navigate back from scale setup", e)
            }
        }
    }

    private suspend fun loadPluginData() {
        try {
            val device = this@BtWifiScaleSetupViewModel.discoveredScale
            var connectedDeviceBroadcastID: String? = null
            ggDeviceService.localSkipDevices.value.forEach {
                if (device?.device?.broadcastId == it && device.connectionStatus == BLEStatus.CONNECTED) {
                    connectedDeviceBroadcastID = it
                } else {
                    ggDeviceService.skipDevice(it, considerForSession = true)
                }
            }
            if (connectedDeviceBroadcastID != null) {
                ggDeviceService.removeSkipDeviceBroadcastID(connectedDeviceBroadcastID)
            }
            val pairedDevices = deviceService.pairedScales.first().map { it.toGGBTDevice() }
            AppLog.d(TAG, "Syncing ${pairedDevices.size} paired devices")
            ggDeviceService.syncDevices(pairedDevices)
        } catch (e: Exception) {
            AppLog.e(TAG, "Error during Bluetooth cleanup", e)
        }
    }

    private suspend fun syncForSetupBleReconnection() {
        try {
            val pairedDevices = deviceService.pairedScales.first().map { it.toGGBTDevice() }
            AppLog.d(TAG, "Syncing ${pairedDevices.size} paired devices for setup BLE reconnection")
            ggDeviceService.syncDevices(pairedDevices)
        } catch (e: Exception) {
            AppLog.e(TAG, "Error during setup BLE reconnection sync", e)
        }
    }

    // --- Measurement / Step-on ---

    private fun stepOn() {
        AppLog.d(TAG, "Starting step on process")
        handleIntent(
            BtWifiScaleSetupIntent.SetStepConnectionState(BtWifiSetupStep.STEP_ON, ConnectionState.Loading),
        )
        try {
            subscribeToLiveData()
        } catch (e: Exception) {
            AppLog.e(TAG, "Error during step on", e)
            handleIntent(
                BtWifiScaleSetupIntent.SetStepConnectionState(BtWifiSetupStep.STEP_ON, ConnectionState.Failed.Error),
            )
        }
    }

    private fun subscribeToLiveData() {
        val scale = discoveredScale ?: run {
            AppLog.e(TAG, "discoveredScale is null when subscribing to live data")
            return
        }
        ggDeviceService.subscribeToLiveData(scale.toGGBTDevice()) {
            when (it) {
                is GGLiveDataResponse.Success -> {
                    handleIntent(
                        BtWifiScaleSetupIntent.SetStepConnectionState(BtWifiSetupStep.STEP_ON, ConnectionState.Success),
                    )
                    onNext()
                }
                else -> Unit
            }
        }
    }

    private fun collectMeasurement() {
        handleIntent(
            BtWifiScaleSetupIntent.SetStepConnectionState(BtWifiSetupStep.MEASUREMENT, ConnectionState.Loading),
        )
        viewModelScope.launch {
            try {
                measurementTimeoutJob = viewModelScope.launch {
                    delay(operationTimeout)
                    if (state.value.currentStep == BtWifiSetupStep.MEASUREMENT) {
                        AppLog.w(TAG, "Measurement collection timeout reached")
                        handleIntent(
                            BtWifiScaleSetupIntent.SetStepConnectionState(
                                BtWifiSetupStep.MEASUREMENT, ConnectionState.Failed.Error,
                            ),
                        )
                    }
                }
                startObservingEntries()
            } catch (e: Exception) {
                measurementTimeoutJob?.cancel()
                measurementTimeoutJob = null
                AppLog.e(TAG, "Error during measurement collection", e)
                handleIntent(
                    BtWifiScaleSetupIntent.SetStepConnectionState(
                        BtWifiSetupStep.MEASUREMENT, ConnectionState.Failed.Error,
                    ),
                )
            }
        }
    }

    // --- Settings update ---

    private fun updateDevicePreferences(dashboardKeys: List<DashboardKey>? = null, preferences: Preferences? = null) {
        viewModelScope.launch {
            try {
                updateSettingsTimeoutJob?.cancel()
                updateSettingsTimeoutJob = viewModelScope.launch {
                    delay(operationTimeout)
                    if (state.value.currentStep == BtWifiSetupStep.UPDATE_SETTINGS) {
                        AppLog.w(TAG, "Update settings timeout reached")
                        setUpdateSettingsError()
                    }
                }
                if (dashboardKeys != null) {
                    dashboardService.updateVisibleKeys(
                        accountId = accountId,
                        keys = dashboardKeys,
                        dashboardType = DashboardType.DASHBOARD_12_METRICS,
                    )
                }
                if (preferences != null) {
                    if (discoveredScale?.connectionStatus != BLEStatus.CONNECTED) {
                        val bid = discoveredScale?.device?.broadcastId ?: discoveredScale?.device?.broadcastIdString
                        if (bid != null) {
                            val connected = withTimeoutOrNull(20_000L) {
                                ggDeviceService.deviceCache.first { cache ->
                                    (cache[bid] as? Device)?.connectionStatus == BLEStatus.CONNECTED
                                }
                            }
                            if (connected == null) {
                                AppLog.w(TAG, "Scale did not reconnect within timeout before UPDATE_SETTINGS")
                                setUpdateSettingsError()
                                return@launch
                            }
                        } else {
                            setUpdateSettingsError()
                            return@launch
                        }
                    }
                    val settingsScale = discoveredScale ?: run {
                        AppLog.e(TAG, "discoveredScale is null during settings update")
                        setUpdateSettingsError()
                        return@launch
                    }
                    val newName = _state.value.usernameForm.username.value
                    val updatedDevice = settingsScale.copy(
                        preferences = preferences.copy(
                            displayName = newName.ifEmpty { preferences.displayName },
                            id = settingsScale.id,
                        ),
                    )
                    discoveredScale = updatedDevice
                    ggDeviceService.updateAccount(updatedDevice.toGGBTDevice()) {
                        when (it) {
                            GGUserActionResponseType.CREATION_COMPLETED, GGUserActionResponseType.UPDATE_COMPLETED -> {
                                viewModelScope.launch {
                                    updateSettingsTimeoutJob?.cancel()
                                    updateSettingsTimeoutJob = null
                                    val savedPrefs = discoveredScale?.preferences
                                    if (savedPrefs != null) {
                                        updateScalePreferences(discoveredScale?.id ?: "", savedPrefs.toR4ScalePreferenceApiModel())
                                    }
                                    AppLog.d(TAG, "Scale settings updated successfully")
                                    handleIntent(
                                        BtWifiScaleSetupIntent.SetStepConnectionState(
                                            BtWifiSetupStep.UPDATE_SETTINGS, ConnectionState.Success,
                                        ),
                                    )
                                    discoveredScale?.let { ggDeviceService.syncDevices(listOf(it.toGGBTDevice())) }
                                    onNext()
                                }
                            }
                            else -> viewModelScope.launch { setUpdateSettingsError() }
                        }
                    }
                    if (!state.value.hasSavedSettings) {
                        val scaleId = discoveredScale?.id ?: settingsScale.id
                        updateScalePreferences(scaleId, preferences.toR4ScalePreferenceApiModel())
                    }
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

    private fun hasCustomizationChanges(): Boolean = state.value.hasSavedSettings

    private fun getTimeZoneInMinutes(): Int {
        val timeZone = TimeZone.getDefault()
        return timeZone.getOffset(System.currentTimeMillis()) / (60 * 1000)
    }

    // --- Permissions ---

    private fun fallbackToErrorsIfPermissionIsDisabled() {
        viewModelScope.launch {
            state.map { it.currentStep }.collect {
                delay(permissionCheckTimeOut)
                handlePermissionBasedErrors()
            }
        }
    }

    private fun initializePermissionsImmediately() {
        viewModelScope.launch {
            try {
                val currentPermissions = permissionService.permissionCallBackFlow.value
                val networkState = try {
                    connectivityObserver.observe().first()
                } catch (e: Exception) {
                    AppLog.d(TAG, "Network state unavailable during initialization (offline mode)")
                    NetworkState(available = false, unAvailable = true)
                }
                updatePermissionsState(currentPermissions, networkState.available)
            } catch (e: Exception) {
                AppLog.e(TAG, "Error initializing permissions immediately", e)
            }
        }
    }

    private fun updatePermissionsState(permissions: GGPermissionStatusMap, isNetworkAvailable: Boolean) {
        val networkStatus = if (isNetworkAvailable) GGPermissionState.ENABLED else GGPermissionState.DISABLED
        val wifiSwitchStatus = permissions[GGPermissionType.WIFI_SWITCH] ?: GGPermissionState.DISABLED
        val updatedWifiSwitchStatus = if (networkStatus == GGPermissionState.ENABLED ||
            wifiSwitchStatus == GGPermissionState.ENABLED
        ) GGPermissionState.ENABLED else GGPermissionState.DISABLED
        val updatedPermissions = permissions.toMutableMap().apply {
            put(GGPermissionType.WIFI_SWITCH, updatedWifiSwitchStatus)
        }
        handleIntent(BtWifiScaleSetupIntent.SetPermissions(updatedPermissions))
    }

    private fun observePermissions() {
        viewModelScope.launch {
            val defaultNetworkState = NetworkState(available = false, unAvailable = true)
            val networkStateFlow = merge(
                flowOf(defaultNetworkState),
                connectivityObserver.observe().catch { e ->
                    AppLog.d(TAG, "Network state unavailable (offline mode): ${e.message}")
                    emit(defaultNetworkState)
                },
            )
            combine(
                permissionService.permissionCallBackFlow.onStart { AppLog.d(TAG, "Starting permission observation") },
                networkStateFlow,
            ) { permissions, networkState ->
                updatePermissionsState(permissions, networkState.available)
                val areRequiredPermissionsEnabled = AppPermissionsHelper.areRequiredPermissionsEnabled(
                    state.value.permissions, setupType = ScaleSetupType.BtWifiR4,
                )
                if (!areRequiredPermissionsEnabled) {
                    handlePermissionBasedErrors()
                } else {
                    val currentStep = state.value.currentStep
                    if (currentStep == BtWifiSetupStep.CUSTOMIZE_SETTINGS || currentStep == BtWifiSetupStep.UPDATE_SETTINGS) {
                        viewModelScope.launch { syncForSetupBleReconnection() }
                    }
                }
            }.catch { e ->
                AppLog.e(TAG, "Error in permission observation flow", e)
                try {
                    val currentPermissions = permissionService.permissionCallBackFlow.value
                    updatePermissionsState(currentPermissions, false)
                } catch (updateError: Exception) {
                    AppLog.e(TAG, "Error updating permissions in offline mode", updateError)
                }
            }.collect { }
        }
    }

    private fun handlePermissionBasedErrors() {
        val areRequiredPermissionsEnabled = AppPermissionsHelper.areRequiredPermissionsEnabled(
            state.value.permissions, setupType = ScaleSetupType.BtWifiR4,
        )
        if (!areRequiredPermissionsEnabled) {
            val disabledPermissions = AppPermissionsHelper.getDisabledPermissionsForSetupType(
                permissionMap = state.value.permissions, setupType = ScaleSetupType.BtWifiR4,
            )
            val isOnlyNetworkPermissionMissing = disabledPermissions.size == 1 &&
                disabledPermissions.contains(GGPermissionType.WIFI_SWITCH)
            when (state.value.currentStep) {
                BtWifiSetupStep.WAKEUP -> {
                    goToPermissionSlide()
                    handleIntent(SetCurrentStep(BtWifiSetupStep.PERMISSIONS))
                }
                BtWifiSetupStep.GATHERING_NETWORK -> setGatheringNetworkFailed()
                BtWifiSetupStep.UPDATE_SETTINGS -> {
                    if (!isOnlyNetworkPermissionMissing) setUpdateSettingsError()
                }
                BtWifiSetupStep.STEP_ON -> {
                    if (!isOnlyNetworkPermissionMissing) {
                        requestPermission(GGPermissionType.BLUETOOTH_SWITCH, true)
                    }
                }
                BtWifiSetupStep.CONNECTING_BLUETOOTH,
                BtWifiSetupStep.DUPLICATES_FOUND,
                BtWifiSetupStep.USER_LIMIT_REACHED -> goToPermissionSlide()
                else -> {}
            }
        }
    }

    private fun goToPermissionSlide() {
        if (discoveredScale?.connectionStatus != BLEStatus.CONNECTED) {
            handleIntent(SetCurrentStep(BtWifiSetupStep.PERMISSIONS))
        }
    }

    private fun requestPermission(permissionType: String, isDuringStepOn: Boolean = false) {
        if (permissionType == GGPermissionType.WIFI_SWITCH) {
            permissionService.requestPermission(permissionType)
            return
        }
        viewModelScope.launch {
            try {
                dialogUtility.permissionAlert(
                    permissionType = permissionType,
                    isScaleSetupRequest = isDuringStepOn,
                    onRequest = { permissionService.requestPermission(permissionType) },
                )
            } catch (e: Exception) {
                AppLog.e(TAG, "Error requesting permission $permissionType", e.toString())
            }
        }
    }

    fun permissionAccess() {
        val currentPermissions = state.value.permissions
        if (currentPermissions[GGPermissionType.BLUETOOTH_SWITCH] != GGPermissionState.ENABLED) {
            handleIntent(BtWifiScaleSetupIntent.RequestPermission(GGPermissionType.BLUETOOTH_SWITCH))
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (currentPermissions[GGPermissionType.NEARBY_DEVICE] != GGPermissionState.ENABLED) {
                handleIntent(BtWifiScaleSetupIntent.RequestPermission(GGPermissionType.NEARBY_DEVICE))
                return
            }
        } else {
            if (currentPermissions[GGPermissionType.LOCATION_SWITCH] != GGPermissionState.ENABLED) {
                handleIntent(BtWifiScaleSetupIntent.RequestPermission(GGPermissionType.LOCATION_SWITCH))
            }
            if (currentPermissions[GGPermissionType.LOCATION] != GGPermissionState.ENABLED) {
                handleIntent(BtWifiScaleSetupIntent.RequestPermission(GGPermissionType.LOCATION))
            }
        }
    }

    // --- Init helpers ---

    private fun loadScaleInfo() {
        handleIntent(BtWifiScaleSetupIntent.SetScaleSku(sku))
    }

    private fun initializeSetup() {
        viewModelScope.launch {
            handleIntent(BtWifiScaleSetupIntent.SetInitialStep(initialStep))
            initializeUsernameForm()
            if (broadcastId != null) {
                discoveredScale = ggDeviceService.deviceCache.value[broadcastId] as? Device
                discoveredScale?.let { setModePreference(it) }
            }
            if (initialStep == BtWifiSetupStep.USER_LIMIT_REACHED && userList != null) {
                handleIntent(BtWifiScaleSetupIntent.SetUserList(userList))
            }
            handleIntent(SetCurrentStep(initialStep))
        }
    }

    private suspend fun initializeUsernameForm() {
        try {
            val activeAccount = accountService.activeAccountFlow.first()
            val username = NameUtils.trimNameForSDK(activeAccount?.firstName)
            _state.value.usernameForm.username.onValueChange(username)
        } catch (e: Exception) {
            _state.value.usernameForm.username.onValueChange("Default")
        }
    }

    private fun setModePreference(scale: Device) {
        try {
            var heartRateEnabled = false
            var allBodyMetricsMode = true
            scale.preferences?.let { preferences ->
                heartRateEnabled = preferences.shouldMeasurePulse ?: false
                allBodyMetricsMode = preferences.shouldMeasureImpedance ?: true
            }
            handleIntent(BtWifiScaleSetupIntent.SetScaleModePreference(allBodyMetricsMode, heartRateEnabled))
        } catch (e: Exception) {
            handleIntent(BtWifiScaleSetupIntent.SetScaleModePreference(true, false))
        }
    }

    private fun subscribeLatestWeight() {
        viewModelScope.launch {
            entryReadService.latestEntry().collect { latestEntry ->
                val latestWeight = when (latestEntry) {
                    is ScaleEntry -> latestEntry.scale.scaleEntry.weight
                    else -> null
                }
                handleIntent(BtWifiScaleSetupIntent.SetLatestWeight(latestWeight))
            }
        }
    }

    private fun loadDashboardKeys() {
        viewModelScope.launch {
            dashboardService.getVisibleKeys().collect { dashboardKeys ->
                handleIntent(BtWifiScaleSetupIntent.SetDashboardKeys(dashboardKeys))
            }
        }
    }

    private fun loadGoalProgress() {
        viewModelScope.launch {
            entryReadService.weightProgress().collect {
                handleIntent(BtWifiScaleSetupIntent.SetGoalProgress(it))
            }
        }
    }

    // --- Error state setters ---

    private fun setGatheringNetworkFailed() {
        handleIntent(
            BtWifiScaleSetupIntent.SetStepConnectionState(
                BtWifiSetupStep.GATHERING_NETWORK, ConnectionState.Failed.Error,
            ),
        )
    }

    private fun setMeasurementFailed() {
        handleIntent(
            BtWifiScaleSetupIntent.SetStepConnectionState(BtWifiSetupStep.MEASUREMENT, ConnectionState.Failed.Error),
        )
        handleIntent(SetCurrentStep(BtWifiSetupStep.MEASUREMENT))
    }

    private fun setUpdateSettingsError() {
        handleIntent(
            BtWifiScaleSetupIntent.SetStepConnectionState(
                BtWifiSetupStep.UPDATE_SETTINGS, ConnectionState.Failed.Error,
            ),
        )
        updateSettingsTimeoutJob?.cancel()
        updateSettingsTimeoutJob = null
    }

    // --- UI helpers ---

    private fun openHelpModal() {
        dialogQueueService.enqueue(
            DialogModel.Custom(
                contentKey = DialogType.HelpPopup,
                params = mapOf(
                    "showGuide" to true,
                    "onGuideClick" to {
                        openProductGuide()
                        dialogQueueService.dismissCurrent()
                    },
                ),
            ),
        )
    }

    private fun openAccucheckModel() {
        dialogQueueService.enqueue(DialogModel.Custom(contentKey = DialogType.AccucheckModal, dismissOnBackPress = true))
    }

    private fun openBiaModel() {
        dialogQueueService.enqueue(
            DialogModel.Custom(
                contentKey = DialogType.BiaModal,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
        )
    }

    private fun openProductGuide() {
        val url = "${AppConfig.PRODUCT_URL}/${state.value.sku}"
        openInAppBrowser(url)
    }

    // --- Lifecycle ---

    override fun onCleared() {
        super.onCleared()
        clearAllTimeouts()
        deviceService.setSetupInProgress(false)
        AppLog.d(TAG, "BtWifiScaleSetupViewModel cleared - all timeouts cancelled")
    }

    private fun clearAllTimeouts() {
        bleManager.cancelPairing()
        scalePairingManager.cancelTimeout()
        wifiManager.cancelTimeout()
        updateSettingsTimeoutJob?.cancel()
        updateSettingsTimeoutJob = null
        measurementTimeoutJob?.cancel()
        measurementTimeoutJob = null
    }

}
