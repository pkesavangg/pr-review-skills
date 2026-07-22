package com.dmdbrands.gurus.weight.app.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.power.interfaces.IPowerSaveModeObserver
import com.dmdbrands.gurus.weight.core.service.BluetoothPreferencesService
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.LogManager
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.repository.IAppRepository
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.services.IAccountFlagService
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IAnalyticsService
import com.dmdbrands.gurus.weight.domain.services.IDashboardService
import com.dmdbrands.gurus.weight.domain.services.IDeviceInfoService
import com.dmdbrands.gurus.weight.domain.services.IEntryReadService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IFeedService
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.library.ggbluetooth.model.GGBPMEntry
import com.dmdbrands.library.ggbluetooth.model.GGBTUser
import com.dmdbrands.library.ggbluetooth.model.GGScaleEntry
import com.dmdbrands.library.ggbluetooth.model.GGScanResponse
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.blewrapper.GGPermissionService
import com.greatergoods.ggInAppMessaging.core.service.GGInAppMessagingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Centralized ViewModel for app-wide state, including theme mode and FCM token.
 *
 * Thin coordinator: cohesive slices are delegated to package-private collaborators —
 * [ScaleConnectionManager] (BLE scan / discovery / reconnect / permissions),
 * [EntrySaveManager] (entry save + reading toasts + baby assignment), and
 * [AuthNotificationManager] (auth events + notifications + IAM) — split out for MOB-1500.
 *
 * @property appRepository The repository providing theme and FCM token flows and actions.
 * @constructor Injects the AppRepository dependency.
 */
@HiltViewModel
class AppViewModel
  @Inject
  constructor(
    private val appRepository: IAppRepository,
    private val entryService: IEntryService,
    private val entryReadService: IEntryReadService,
    private val logManager: LogManager,
    private val appNavigationService: IAppNavigationService,
    private val dashboardService: IDashboardService,
    private val accountService: IAccountService,
    private val dialogUtility: IDialogUtility,
    private val deviceService: IDeviceService,
    private val ggPermissionService: GGPermissionService,
    private val ggDeviceService: GGDeviceService,
    private val deviceInfoService: IDeviceInfoService,
    private val bluetoothPreferencesService: BluetoothPreferencesService,
    private val feedService: IFeedService,
    private val ggInAppMessagingService: GGInAppMessagingService,
    private val accountFlagService: IAccountFlagService,
    private val analyticsService: IAnalyticsService,
    private val powerSaveModeObserver: IPowerSaveModeObserver,
  ) : BaseIntentViewModel<AppState, AppIntent>(
      reducer = AppReducer(),
    ) {
    companion object {
      private const val TAG = "AppViewModel"
    }

    override fun provideInitialState(): AppState = AppState()

    /**
     * Device Power Saving Mode state, surfaced to the Compose tree via LocalPowerSaveMode so
     * components can drop continuous animations while Battery Saver is on (MOB-226).
     */
    val powerSaveMode: StateFlow<Boolean> =
      powerSaveModeObserver
        .observe()
        .stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000),
          initialValue = powerSaveModeObserver.isPowerSaveMode(),
        )

    private var currentAccountId: String? = null

    // Retained on the coordinator because it is set reflectively by AppViewModelTest to drive the
    // scale-discovered popup routing; ScaleConnectionManager reads/writes it through get/set lambdas.
    private var sku: String? = null

    private val entrySaveManager: EntrySaveManager =
      EntrySaveManager(
        scope = viewModelScope,
        getCurrentAccountId = { currentAccountId },
        provideNavigation = { navigationService },
        provideDialogQueue = { dialogQueueService },
        deviceService = deviceService,
        entryService = entryService,
        accountService = accountService,
        accountFlagService = accountFlagService,
        onShowReadingToast = { entry, readingType, sourceSku ->
          readingAssignmentManager.showReadingToast(entry, readingType, sourceSku)
        },
      )

    private val readingAssignmentManager: ReadingAssignmentManager =
      ReadingAssignmentManager(
        scope = viewModelScope,
        getCurrentAccountId = { currentAccountId },
        provideNavigation = { navigationService },
        provideDialogQueue = { dialogQueueService },
        provideProductSelection = { productSelectionManager },
        entryService = entryService,
        onCheckAccountFlags = { entrySaveManager.checkAccountFlags(it) },
      )

    private val scaleConnectionManager: ScaleConnectionManager =
      ScaleConnectionManager(
        scope = viewModelScope,
        getState = { state.value },
        onIntent = { handleIntent(it) },
        getCurrentAccountId = { currentAccountId },
        provideNavigation = { navigationService },
        provideDialogQueue = { dialogQueueService },
        ggPermissionService = ggPermissionService,
        ggDeviceService = ggDeviceService,
        deviceService = deviceService,
        accountService = accountService,
        entryReadService = entryReadService,
        dialogUtility = dialogUtility,
        onHandleDeviceResponse = { deviceResponseManager.handleDeviceResponse(it) },
        onEntryResponse = { entrySaveManager.handleEntryResponse(it) },
        onSessionObserversReady = { authNotificationManager.startSessionObservers(it) },
      )

    private val deviceResponseManager: DeviceResponseManager =
      DeviceResponseManager(
        scope = viewModelScope,
        getState = { state.value },
        onIntent = { handleIntent(it) },
        getCurrentAccountId = { currentAccountId },
        getSku = { sku },
        setSku = { sku = it },
        provideNavigation = { navigationService },
        provideDialogQueue = { dialogQueueService },
        ggDeviceService = ggDeviceService,
        deviceService = deviceService,
        bluetoothPreferencesService = bluetoothPreferencesService,
        accountService = accountService,
        analyticsService = analyticsService,
        getLatestPairedScales = { scaleConnectionManager.latestPairedScales() },
        onCheckWeightOnlyAlert = { scaleConnectionManager.checkCanShowWeightOnlyModeAlert() },
      )

    private val authNotificationManager: AuthNotificationManager =
      AuthNotificationManager(
        scope = viewModelScope,
        onIntent = { handleIntent(it) },
        provideNavigation = { navigationService },
        provideDialogQueue = { dialogQueueService },
        appNavigationService = appNavigationService,
        accountService = accountService,
        dashboardService = dashboardService,
        entryService = entryService,
        feedService = feedService,
        dialogUtility = dialogUtility,
        ggInAppMessagingService = ggInAppMessagingService,
        onStopScan = { scaleConnectionManager.stopScan() },
        onResetScaleDiscoveredState = { deviceResponseManager.resetScaleDiscoveredState() },
        onCancelAccountObservers = { scaleConnectionManager.cancelAccountObservers() },
        onStartObserversOnly = { account, fromLoadingScreen ->
          scaleConnectionManager.startObserversOnly(account, fromLoadingScreen)
        },
        onStartScan = { scaleConnectionManager.startScan() },
        onSyncScales = { scaleConnectionManager.syncScales() },
      )

    init {

      // Drive Compose theme directly from the persisted preference so toggling
      // appearance repaints surfaces immediately, without relying on
      // setDefaultNightMode/UiModeManager to round-trip through Configuration
      // (which is unreliable on API < 31).
      viewModelScope.launch {
        appRepository.themeModeFlow
          .distinctUntilChanged()
          .collect { mode ->
            handleIntent(AppIntent.SetThemeMode(mode))
          }
      }

      // Initialize and maintain currentAccountId globally
      viewModelScope.launch {
        accountService.activeAccountFlow.collect {
          currentAccountId = it?.id
        }
      }

      // Once per launch, after the account is available: commit any entries left in the swipe-delete
      // Undo window by a previous process kill (the pendingDelete flag persists). (MOB-1173)
      viewModelScope.launch {
        accountService.activeAccountFlow.firstOrNull { it != null }
        entryService.commitPendingDeletes()
      }

      viewModelScope.launch {
        try {
          logManager.cleanupOldLogs(5)
          AppLog.i("MainActivity", "Cleaning up old logs")
        } catch (e: Exception) {
          AppLog.e("MainActivity", "Failed to cleanup old logs", e)
        }

        // Auth-event observers. Token migration (DataStore → EncryptedSharedPreferences) and
        // loading tokens into TokenManager now happen in LoadingScreenViewModel.waitForMigration()
        // so the startup login check can't race ahead of them (MOB-1537 / MOB-1526).
        try {
          authNotificationManager.initEvents()
        } catch (e: Exception) {
          AppLog.e(TAG, "Failed to initialise auth events", e)
        }
      }
    }

    override fun handleIntent(intent: AppIntent) {
      when (intent) {
        is AppIntent.OnPopUpConnect -> deviceResponseManager.onPopUpConnect()

        is AppIntent.OnPopUpDismiss -> deviceResponseManager.onPopUpDismiss(shouldSkipDevice = true)

        else -> {}
      }
      super.handleIntent(intent)
    }

    // -------------------------------------------------------------------------
    // Test-surface delegates. These stay declared on AppViewModel (identical names/signatures) so
    // AppViewModelTest's reflection (declaredMemberFunctions / getDeclaredField) keeps working; each
    // forwards to the collaborator that now owns the behaviour.
    // -------------------------------------------------------------------------

    internal fun selectDuplicateUserToken(
      userList: List<GGBTUser>,
      displayName: String?,
      localToken: String?,
    ): String? = deviceResponseManager.selectDuplicateUserToken(userList, displayName, localToken)

    private fun handleDeviceResponse(deviceResponse: GGScanResponse.DeviceDetail) =
      deviceResponseManager.handleDeviceResponse(deviceResponse)

    private fun handleEntryResponse(entryResponse: GGScanResponse.Entry) =
      entrySaveManager.handleEntryResponse(entryResponse)

    private fun checkCanShowWeightOnlyModeAlert() =
      scaleConnectionManager.checkCanShowWeightOnlyModeAlert()

    private fun checkAndRequestNotificationPermission() =
      scaleConnectionManager.checkAndRequestNotificationPermission()

    private fun requestPermissions(permissionType: String) =
      scaleConnectionManager.requestPermissions(permissionType)

    private fun navigateToAppPermissions() =
      scaleConnectionManager.navigateToAppPermissions()

    private fun stopScan() = scaleConnectionManager.stopScan()

    private fun saveEntry(ggEntry: List<GGScaleEntry>) = entrySaveManager.saveEntry(ggEntry)

    private fun saveBpmEntry(ggEntries: List<GGBPMEntry>) = entrySaveManager.saveBpmEntry(ggEntries)

    private fun checkAccountFlags(trigger: String) = entrySaveManager.checkAccountFlags(trigger)

    private fun showAssignMeasurementDialog(
      reading: String,
      entry: List<ScaleEntry>,
      preSelectedBabyId: String? = null,
      previousEntryIds: List<Long> = emptyList(),
      sourceSku: String? = null,
    ) = readingAssignmentManager.showAssignMeasurementDialog(reading, entry, preSelectedBabyId, previousEntryIds, sourceSku)

    private suspend fun assignReadingToBaby(
      reading: String,
      entry: List<ScaleEntry>,
      babyId: String,
      babies: List<BabyProfile>,
      previousEntryIds: List<Long>,
      sourceSku: String? = null,
    ) = readingAssignmentManager.assignReadingToBaby(reading, entry, babyId, babies, previousEntryIds, sourceSku)
  }
