package com.dmdbrands.gurus.weight.features.DeviceSetup.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.service.BluetoothPreferencesService
import com.dmdbrands.gurus.weight.core.shared.utilities.NameUtils
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.model.storage.toGGBTDevice
import com.dmdbrands.gurus.weight.domain.repository.IDeviceRepository
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IDashboardService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IEntryReadService
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.BtWifiSetupStep
import com.dmdbrands.gurus.weight.features.DeviceSetup.manager.BLEDiscoveryManager
import com.dmdbrands.gurus.weight.features.DeviceSetup.manager.BtWifiDialogPresenter
import com.dmdbrands.gurus.weight.features.DeviceSetup.manager.BtWifiExitManager
import com.dmdbrands.gurus.weight.features.DeviceSetup.manager.BtWifiMeasurementManager
import com.dmdbrands.gurus.weight.features.DeviceSetup.manager.BtWifiPermissionsManager
import com.dmdbrands.gurus.weight.features.DeviceSetup.manager.BtWifiSettingsManager
import com.dmdbrands.gurus.weight.features.DeviceSetup.manager.IBLEDiscoveryManager
import com.dmdbrands.gurus.weight.features.DeviceSetup.manager.IDevicePairingManager
import com.dmdbrands.gurus.weight.features.DeviceSetup.manager.IWiFiConfigManager
import com.dmdbrands.gurus.weight.features.DeviceSetup.manager.DevicePairingManager
import com.dmdbrands.gurus.weight.features.DeviceSetup.manager.WiFiConfigManager
import com.dmdbrands.gurus.weight.features.DeviceSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.BtWifiScaleSetupIntent
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.BtWifiScaleSetupIntent.SetCurrentStep
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.BtWifiScaleSetupReducer
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.BtWifiScaleSetupState
import com.dmdbrands.gurus.weight.features.DeviceSetup.strings.DeviceSetupStrings
import com.dmdbrands.gurus.weight.features.appPermissions.helper.AppPermissionsHelper
import com.dmdbrands.gurus.weight.features.DeviceSetup.helper.switchActiveProductAfterSetup
import com.dmdbrands.gurus.weight.features.common.enums.DeviceSetupType
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionType
import com.dmdbrands.library.ggbluetooth.enums.GGScanResponseType
import com.dmdbrands.library.ggbluetooth.model.GGBTUser
import com.dmdbrands.library.ggbluetooth.model.GGScanResponse
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.blewrapper.GGPermissionService
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Coordinator ViewModel for the BtWifiScaleSetupScreen.
 *
 * Owns navigation between setup steps and delegates each cohesive slice to a focused
 * collaborator: BLE discovery / WiFi config / scale pairing (existing managers) plus
 * permissions, measurement, settings, exit teardown, and dialogs (MOB-1501 split to clear
 * detekt's LargeClass limit). The ViewModel stays the thin state holder and step router.
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
) : DeviceSetupViewmodel<BtWifiScaleSetupState, BtWifiScaleSetupIntent>(
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
    private val cleanupTimeoutMs: Long = 10_000L

    private var isScaleConnected: Boolean = discoveredScale?.connectionStatus == BLEStatus.CONNECTED
    private var isScaleSaved: Boolean = false
    private var accountId: String? = null

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
        requestNotificationPermission = { permissionsManager.requestPermission(GGPermissionType.NOTIFICATION) },
    )

    private val scalePairingManager: IDevicePairingManager = DevicePairingManager(
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
        setIsScaleSaved = { isScaleSaved = it },
        getAccountId = { accountId },
        onNext = ::onNext,
        enqueueDialog = { dialogQueueService.enqueue(it) },
    )

    private val permissionsManager = BtWifiPermissionsManager(
        ggDeviceService = ggDeviceService,
        permissionService = permissionService,
        connectivityObserver = connectivityObserver,
        deviceService = deviceService,
        dialogUtility = dialogUtility,
        scope = viewModelScope,
        permissionCheckTimeOut = permissionCheckTimeOut,
        getState = { state.value },
        getStateFlow = { state },
        onIntent = ::handleIntent,
        getDiscoveredScale = { discoveredScale },
        onUpdateSettingsError = { settingsManager.setUpdateSettingsError() },
        onMeasurementFailed = { measurementManager.setMeasurementFailed() },
        cancelPairingTimeout = { scalePairingManager.cancelTimeout() },
    )

    private val measurementManager = BtWifiMeasurementManager(
        ggDeviceService = ggDeviceService,
        scope = viewModelScope,
        operationTimeout = operationTimeout,
        getState = { state.value },
        onIntent = ::handleIntent,
        getDiscoveredScale = { discoveredScale },
        onNext = ::onNext,
        startObservingEntries = ::startObservingEntries,
        // Re-sync paired devices so the R4 re-establishes its BLE link on a measurement retry (MOB-1580).
        onSyncForBleReconnection = {
            try {
                val pairedDevices = deviceService.pairedScales.first().map { it.toGGBTDevice() }
                AppLog.d(TAG, "Syncing ${pairedDevices.size} paired devices for setup BLE reconnection")
                ggDeviceService.syncDevices(pairedDevices)
            } catch (e: Exception) {
                AppLog.e(TAG, "Error during setup BLE reconnection sync", e)
            }
        },
    )

    private val settingsManager = BtWifiSettingsManager(
        ggDeviceService = ggDeviceService,
        deviceService = deviceService,
        deviceRepository = deviceRepository,
        dashboardService = dashboardService,
        entryReadService = entryReadService,
        scope = viewModelScope,
        operationTimeout = operationTimeout,
        getState = { state.value },
        onIntent = ::handleIntent,
        getDiscoveredScale = { discoveredScale },
        setDiscoveredScale = { discoveredScale = it },
        getAccountId = { accountId },
        onNext = ::onNext,
    )

    private val exitManager = BtWifiExitManager(
        ggDeviceService = ggDeviceService,
        deviceService = deviceService,
        scope = viewModelScope,
        initialStep = initialStep,
        cleanupTimeoutMs = cleanupTimeoutMs,
        getState = { state.value },
        onIntent = ::handleIntent,
        getDiscoveredScale = { discoveredScale },
        getIsScaleSaved = { isScaleSaved },
        clearAllTimeouts = ::clearAllTimeouts,
        enqueueDialog = { dialogQueueService.enqueue(it) },
        showLoader = { dialogQueueService.showLoader(it) },
        dismissLoader = { dialogQueueService.dismissLoader() },
        navigateBack = { navigationService.navigateBack() },
        switchActiveProductAfterSetup = {
            // Auto-switch the dashboard header to the newly added scale (MOB-422).
            productSelectionManager.switchActiveProductAfterSetup(ProductSelection.MyWeight)
        },
    )

    private val dialogPresenter = BtWifiDialogPresenter(
        scope = viewModelScope,
        getState = { state.value },
        onIntent = ::handleIntent,
        enqueueDialog = { dialogQueueService.enqueue(it) },
        dismissCurrentDialog = { dialogQueueService.dismissCurrent() },
        showLoader = { dialogQueueService.showLoader(it) },
        dismissLoader = { dialogQueueService.dismissLoader() },
        openInAppBrowser = { openInAppBrowser(it) },
    )

    init {
        deviceService.setSetupInProgress(true)
        loadScaleInfo()
        permissionsManager.initializePermissionsImmediately()
        permissionsManager.observePermissions()
        observeStepChanges()
        initializeSetup()
        subscribeLatestWeight()
        permissionsManager.fallbackToErrorsIfPermissionIsDisabled()
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
            is BtWifiScaleSetupIntent.UpdateSettings ->
                settingsManager.updateDevicePreferences(intent.dashboardKeys, intent.preferences)
            BtWifiScaleSetupIntent.ShowSavingLoader -> dialogPresenter.showSavingLoader()
            BtWifiScaleSetupIntent.RefreshNetworks -> onRefreshNetworks()
            BtWifiScaleSetupIntent.HandlePasswordNetworkStatus -> wifiManager.handlePasswordNetworkStatus()
            is BtWifiScaleSetupIntent.RequestPermission -> permissionsManager.requestPermission(intent.permissionType)
            is BtWifiScaleSetupIntent.ExitSetup -> onExitSetup(intent.isSetupFinished)
            BtWifiScaleSetupIntent.OpenHelp -> dialogPresenter.openHelpModal()
            BtWifiScaleSetupIntent.OpenAccucheckModal -> dialogPresenter.openAccucheckModel()
            BtWifiScaleSetupIntent.OpenBiaModal -> dialogPresenter.openBiaModel()
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
                measurementManager.cancelMeasurementTimeout()
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
                    handleStepChange(currentStep, currentState)
                }
                previousStep = currentStep
            }
        }
    }

    private fun handleStepChange(currentStep: BtWifiSetupStep, currentState: BtWifiScaleSetupState) {
        when (currentStep) {
            BtWifiSetupStep.WAKEUP -> bleManager.startPairing()
            BtWifiSetupStep.CONNECTING_BLUETOOTH -> scalePairingManager.connectToBluetooth()
            BtWifiSetupStep.DUPLICATES_FOUND ->
                handleIntent(BtWifiScaleSetupIntent.UpdateNextButtonText(DeviceSetupStrings.SetupButtons.Save))
            BtWifiSetupStep.GATHERING_NETWORK -> {
                if (!AppPermissionsHelper.areRequiredPermissionsEnabled(
                        state.value.permissions, setupType = DeviceSetupType.BtWifiR4,
                    )
                ) {
                    permissionsManager.setGatheringNetworkFailed()
                } else {
                    wifiManager.gatherNetworks()
                }
            }
            BtWifiSetupStep.WIFI_PASSWORD ->
                handleIntent(BtWifiScaleSetupIntent.UpdateNextButtonText(DeviceSetupStrings.SetupButtons.Connect))
            BtWifiSetupStep.CONNECTING_WIFI -> wifiManager.connectToWifi()
            BtWifiSetupStep.CUSTOMIZE_SETTINGS -> {
                settingsManager.loadDashboardKeys()
                settingsManager.loadGoalProgress()
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
                        state.value.permissions, setupType = DeviceSetupType.BtWifiR4,
                    )
                ) {
                    val disabledPermissions = AppPermissionsHelper.getDisabledPermissionsForSetupType(
                        permissionMap = state.value.permissions, setupType = DeviceSetupType.BtWifiR4,
                    )
                    val isOnlyNetworkPermissionMissing = disabledPermissions.size == 1 &&
                        disabledPermissions.contains(GGPermissionType.WIFI_SWITCH)
                    if (!isOnlyNetworkPermissionMissing) measurementManager.setMeasurementFailed() else measurementManager.stepOn()
                } else {
                    measurementManager.stepOn()
                }
            }
            BtWifiSetupStep.MEASUREMENT -> {
                val measurementConnectionState = currentState.stepConnectionStates[BtWifiSetupStep.MEASUREMENT]
                if (measurementConnectionState !is ConnectionState.Failed) {
                    measurementManager.collectMeasurement()
                }
            }
            else -> {}
        }
    }

    private fun onNext() {
        if (exitManager.isExiting) return
        val currentState = state.value
        if (currentState.isLastStep) handleIntent(BtWifiScaleSetupIntent.ExitSetup(true))
        when (currentState.currentStep) {
            BtWifiSetupStep.SCALE_INFO -> {
                viewModelScope.launch {
                    val arePermissionsEnabled = AppPermissionsHelper.areRequiredPermissionsEnabled(
                        currentState.permissions, setupType = DeviceSetupType.BtWifiR4,
                    )
                    if (arePermissionsEnabled) {
                        handleIntent(SetCurrentStep(BtWifiSetupStep.WAKEUP))
                    } else {
                        permissionsManager.permissionAccess()
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
                title = DeviceSetupStrings.SkipBtWifiPermissions.Title,
                message = DeviceSetupStrings.SkipBtWifiPermissions.Message,
                confirmText = DeviceSetupStrings.SkipBtWifiPermissions.Skip,
                cancelText = DeviceSetupStrings.SkipBtWifiPermissions.Back,
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
            BtWifiSetupStep.STEP_ON -> measurementManager.stepOn()
            BtWifiSetupStep.UPDATE_SETTINGS -> {
                settingsManager.cancelUpdateSettingsTimeout()
                goToCustomiseSettings()
            }
            BtWifiSetupStep.MEASUREMENT -> {
                measurementManager.cancelMeasurementTimeout()
                measurementManager.retryMeasurement()
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

    private fun onExitSetup(isSetupFinished: Boolean) {
        exitManager.onExitSetup(isSetupFinished)
    }

    private fun hasCustomizationChanges(): Boolean = state.value.hasSavedSettings

    /**
     * Retained on the ViewModel because [BtWifiScaleSetupViewModelTest] invokes it reflectively
     * to simulate a permission drop; delegates to [permissionsManager].
     */
    private fun handlePermissionBasedErrors() {
        permissionsManager.handlePermissionBasedErrors()
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
        settingsManager.cancelUpdateSettingsTimeout()
        measurementManager.cancelMeasurementTimeout()
    }
}
