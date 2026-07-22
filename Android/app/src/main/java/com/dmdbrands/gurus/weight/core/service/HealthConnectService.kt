package com.dmdbrands.gurus.weight.core.service

import androidx.activity.ComponentActivity
import com.dmdbrands.gurus.weight.core.di.ApplicationScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.shared.utilities.DeviceInfoUtil
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.integrations.IntegratedDeviceInfo
import com.dmdbrands.gurus.weight.domain.model.integrations.IntegrationData
import com.dmdbrands.gurus.weight.domain.model.integrations.IntegrationOperationType
import com.dmdbrands.gurus.weight.domain.model.integrations.IntegrationPreferences
import com.dmdbrands.gurus.weight.domain.model.integrations.IntegrationType
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BabyEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BpmEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBpmSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.toPeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.toBpmSummary
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IEntryRepository
import com.dmdbrands.gurus.weight.domain.repository.IHealthConnectRepository
import com.dmdbrands.gurus.weight.domain.repository.IIntegrationRepository
import com.dmdbrands.gurus.weight.domain.services.IHealthConnectService
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.integration.model.Integrations
import com.dmdbrands.gurus.weight.features.integration.strings.HealthConnectStrings
import com.greatergoods.libs.healthconnect.HealthConnect
import com.greatergoods.libs.healthconnect.enums.DataType
import com.greatergoods.libs.healthconnect.enums.HealthConnectPermissionStatus
import com.greatergoods.libs.healthconnect.enums.HealthConnectRequestStatus
import com.greatergoods.libs.healthconnect.enums.HealthConnectStatus
import com.greatergoods.libs.healthconnect.model.BloodPressureData
import com.greatergoods.libs.healthconnect.model.HealthConnectData
import com.greatergoods.libs.healthconnect.model.HealthConnectOptions
import com.greatergoods.libs.healthconnect.model.HealthConnectResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton
import android.app.Activity
import android.content.Context
import android.content.Intent

/**
 * Implementation of Health Connect service for managing Health Connect integration.
 * This service uses the HealthConnect library's built-in load() functionality for permission handling.
 */
@Singleton
class HealthConnectService @Inject constructor(
  private val context: Context,
  private val healthConnectRepository: IHealthConnectRepository,
  private val accountRepository: IAccountRepository,
  connectivityObserver: IConnectivityObserver,
  dialogQueueService: IDialogQueueService,
  appNavigationService: IAppNavigationService,
  private val entryRepository: IEntryRepository, // Add entry repository for fetching entries
  private val integrationRepository: IIntegrationRepository, // Inject IntegrationRepository for integrations flow
  @ApplicationScope private val appScope: CoroutineScope,
) : BaseService(connectivityObserver, dialogQueueService, appNavigationService), IHealthConnectService {

  // Core Health Connect components
  private lateinit var healthConnect: HealthConnect
  private lateinit var currentActivity: Activity
  private var currentIntegrations: Integrations? = null

  // Service state
  private val tag = "HealthConnectService"
  private var isLoaded = false

  // Global account ID - automatically updated when account changes
  private var currentAccountId: String? = null
  /**
   * Checks if there's an active account.
   * @return true if there's an active account, false otherwise
   */
  private fun hasActiveAccount(): Boolean = currentAccountId != null

  /**
   * Gets the current account ID or throws an exception if none is available.
   * @return The current account ID
   * @throws IllegalStateException if no account is active
   */
  private fun requireCurrentAccountId(): String {
    return currentAccountId ?: throw IllegalStateException("No active account found")
  }

  // State flows
  private val _outOfSyncState = MutableStateFlow(false)
  override val outOfSyncState: Flow<Boolean> = _outOfSyncState.asStateFlow()

  /**
   * The requesting permissions for Health Connect integration.
   * Defines what data types the app needs read/write access to.
   */
  override val requestingPermissions = HealthConnectOptions(
    writeTypes = setOf(
      DataType.Weight,
      DataType.BodyFat,
      DataType.LeanBodyMass,
      DataType.BasalMetabolicRate,
      DataType.RestingHeartRate,
      DataType.BoneMass,
      DataType.BloodPressure,
    ),
  )

  init {

    appScope.launch {
      AppLog.d(tag, "Health connect service gets initialised")
      accountRepository.getActiveAccount().collect { it ->
        currentAccountId = it?.id
      }
    }
  }

  /**
   * Initializes the Health Connect service.
   * The HealthConnect library handles its own initialization,
   * so this method just creates the instance.
   *
   * @param activity The Activity context for permission handling
   */
  override fun load(activity: ComponentActivity) {
    try {
      AppLog.i(tag, "Initializing Health Connect service...")
      currentActivity = activity
      healthConnect = HealthConnect(activity)
      isLoaded = true
      AppLog.i(tag, "Health Connect service initialized successfully.")
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to initialize Health Connect service", e)
      isLoaded = false
    }
  }

  /**
   * Legacy method for backward compatibility.
   * This now delegates to the new initialize method.
   */
  override fun initializeHealthConnect(activity: ComponentActivity) {
    if (!isLoaded) {
      load(activity)
    }
  }

  /**
   * Gets the device ID for health connect integration.
   * Uses the device's unique identifier (ANDROID_ID).
   */
  private fun getDeviceId(): String {
    return DeviceInfoUtil.getDeviceUUID(context)
  }

  /**
   * Checks if Health Connect is available on the device.
   */
  override suspend fun checkAvailability(): Boolean {
    return try {
      healthConnect.isAvailable()
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to check Health Connect availability", e)
      false
    }
  }

  /**
   * Gets the current Health Connect status.
   */
  override suspend fun healthConnectStatus(): HealthConnectStatus {
    return try {
      healthConnect.getStatus()
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to get Health Connect status", e)
      HealthConnectStatus.UNAVAILABLE
    }
  }

  /**
   * Checks the current permission status.
   */
  override suspend fun checkPermissionStatus(): HealthConnectPermissionStatus {
    return try {
      healthConnect.getPermissionStatus(requestingPermissions)
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to check permission status", e)
      HealthConnectPermissionStatus.NONE
    }
  }

  /**
   * Requests Health Connect authorization using the callback-based approach.
   * This matches the library's async pattern and handles results properly.
   */
  override suspend fun requestAuthorization(callback: (HealthConnectRequestStatus) -> Unit) {
    try {
      // Use the library's callback-based approach directly
      healthConnect.requestAuthorization(requestingPermissions) { result ->
        AppLog.i(tag, "Authorization completed with result: $result")
        callback(result)
      }
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to request authorization", e)
      callback(HealthConnectRequestStatus.CANCELLED)
    }
  }

  /**
   * Opens the Health Connect app or settings.
   */
  override suspend fun openHealthConnect(isFromSetup: Boolean): Boolean {
    return try {
      val activity = currentActivity
      val accountId = requireCurrentAccountId()
      run {
        val result = healthConnect.launchHealthConnect(activity, false)
        if (isFromSetup) {
          healthConnectRepository.setOpen(accountId, true)
        }
        AppLog.i(tag, "Health Connect launch result: $result")
        result
      }
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to open Health Connect", e)
      false
    }
  }

  /**
   * Revokes all Health Connect permissions.
   */
  override suspend fun revokePermission(): Boolean {
    return try {
      val result = healthConnect.revokeAllPermissions()
      when (result) {
        is HealthConnectResult.Success -> {
          AppLog.i(tag, "Successfully revoked Health Connect permissions")
          true
        }

        is HealthConnectResult.Error -> {
          AppLog.e(tag, "Failed to revoke permissions", result.toString())
          false
        }
      }
    } catch (e: Exception) {
      AppLog.e(tag, "Exception while revoking permissions", e)
      false
    }
  }

  override suspend fun checkIfAlreadyUsed(): Boolean {
    return try {
      val currentAccount = accountRepository.getActiveAccount().first()
      if (currentAccount == null) {
        AppLog.w(tag, "No current account found for integration check")
        return false
      }

      val allAccountData = healthConnectRepository.getAccountDataMap()
      val assignedToAccountId = allAccountData.values
        .firstOrNull { it.hasAssignedTo() }
        ?.assignedTo
      val isIntegrated: Boolean = assignedToAccountId == null || assignedToAccountId.isEmpty() || assignedToAccountId == currentAccount.id
      AppLog.d(tag, "Health Connect integration check for account ${currentAccount.id}: $isIntegrated")
      isIntegrated
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to check Health Connect integration", e)
      false
    }
  }

  /**
   * Checks if Health Connect is integrated with the current account.
   * Compares the assignedTo value with the current account ID.
   *
   * @return true if Health Connect is assigned to the current account, false otherwise
   */
  override suspend fun checkIntegrated(): Boolean {
    return try {
      val accountId = currentAccountId
      if (accountId == null) {
        AppLog.w(tag, "No active account found for integration check")
        return false
      }

      val allAccountData = healthConnectRepository.getAccountDataMap()
      val assignedToAccountId = allAccountData.values
        .firstOrNull { it.hasAssignedTo() }
        ?.assignedTo

      val isIntegrated = assignedToAccountId == accountId
      AppLog.d(tag, "Health Connect integrated check for account $accountId: $isIntegrated")
      return isIntegrated
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to check Health Connect integrated status", e)
      false
    }
  }

  /**
   * Handles new intents for privacy policy and permissions rationale.
   * This method forwards the intent to the HealthConnect library for processing.
   * The library will handle the callback appropriately based on the intent action.
   *
   * @param intent The new intent to handle
   */
  override fun handleOnNewIntent(intent: Intent?) {
    if (!isLoaded) {
      AppLog.w(tag, "Health Connect service not loaded, ignoring intent")
      return
    }

    try {
      when (intent?.action) {
        HealthConnect.ACTION_SHOW_PERMISSIONS_RATIONALE,
        HealthConnect.ACTION_VIEW_PERMISSION_USAGE -> {
          AppLog.i(tag, "Forwarding Health Connect intent: ${intent.action}")
          healthConnect.handleOnNewIntent(intent)
        }
      }
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to handle Health Connect intent", e)
    }
  }

  override suspend fun syncAllData(fromOutOfSync: Boolean): Boolean {
    dialogQueueService.showLoader("Syncing...")
    AppLog.i(tag, "Data syncing")

    if (!hasActiveAccount()) {
      AppLog.w(tag, "No active account found")
      dialogQueueService.dismissLoader()
      return false
    }

    val accountId = requireCurrentAccountId()
    try {
      // Check if Health Connect is integrated with current account
      val isIntegrated = checkIfAlreadyUsed()
      if (!isIntegrated) {
        AppLog.w(tag, "Health Connect not integrated with current account")
        dialogQueueService.dismissLoader()
        return false
      }

      val entries: List<Entry> = entryRepository.getEntriesByAccount(accountId)
      val combined = buildCombinedSyncPayload(entries)

      if (entries.isNotEmpty() && combined.isEmpty()) {
        // Real failure: the user has entries but none reached HC due to bad data.
        // Surface the same error dialog as a sync exception so the user is not misled
        // by a "Sync complete" toast.
        AppLog.e(tag, "syncAllData: ${entries.size} entries had no syncable HC data after filtering")
        showDataNotSyncedDialog()
        return false
      }

      if (combined.isNotEmpty()) {
        AppLog.d(tag, "Syncing ${combined.size} records to Health Connect")
        healthConnect.saveData(combined)
      }
      dialogQueueService.dismissLoader()
      if (!fromOutOfSync) {
        dialogQueueService.showToast(Toast.Simple(HealthConnectStrings.ToastStrings.syncToast))
      } else {
        dialogQueueService.showToast(Toast.Simple(HealthConnectStrings.ToastStrings.syncHc))
      }
      return true
    } catch (e: CancellationException) {
      // Coroutine cancellation (e.g. user navigated away) is not a sync failure;
      // re-throw so structured concurrency unwinds correctly. Protect the loader
      // dismissal so any service-side throw cannot replace the cancellation cause.
      try {
        dialogQueueService.dismissLoader()
      } catch (suppressed: Throwable) {
        e.addSuppressed(suppressed)
      }
      throw e
    } catch (e: Exception) {
      AppLog.e(tag, "User denied health connect permission or sync failed", e)
      showDataNotSyncedDialog()
      return false
    } finally {
      dialogQueueService.dismissLoader()
    }
  }

  /**
   * Builds the combined HC payload from [entries], aggregating body-scale and BPM data
   * via the shared [buildBodyScaleData] / [buildBpmData] helpers. Entries with
   * malformed `entryTimestamp` are skipped (each is already logged at .e inside
   * [parseTimestampOrNull]); the aggregate count is logged at .w as supplementary context
   * so the crash reporter doesn't see duplicate signal.
   */
  private fun buildCombinedSyncPayload(entries: List<Entry>): List<HealthConnectData> {
    val summaries = entries.mapNotNull { it.toPeriodBodyScaleSummary() }
    val bpmEntries = entries.filterIsInstance<BpmEntry>()
    var skipped = 0
    val bodyScaleData = summaries.flatMap { summary ->
      val timestamp = parseTimestampOrNull(summary.entryTimestamp)
      if (timestamp == null) { skipped++; return@flatMap emptyList() }
      buildBodyScaleData(summary, timestamp)
    }
    val bpmData = bpmEntries.flatMap { entry ->
      val summary = entry.toBpmSummary()
      val timestamp = parseTimestampOrNull(summary.entryTimestamp)
      if (timestamp == null) { skipped++; return@flatMap emptyList() }
      buildBpmData(summary, timestamp)
    }
    if (skipped > 0) {
      AppLog.w(tag, "syncAllData: skipped $skipped entries with malformed timestamps")
    }
    return bodyScaleData + bpmData
  }

  /** Dismisses the sync loader and shows the user-facing "data not synced" alert. */
  private fun showDataNotSyncedDialog() {
    dialogQueueService.dismissLoader()
    dialogQueueService.showDialog(
      DialogModel.Alert(
        title = HealthConnectStrings.dataNotSynced.title,
        message = HealthConnectStrings.dataNotSynced.message,
        dismissText = HealthConnectStrings.ActionButtons.close,
        onDismiss = { dialogQueueService.dismissCurrent() },
      ),
    )
  }

  /**
   * Pushes [entries] to Health Connect. Logs and rethrows on failure so the caller
   * can surface UX (e.g. permission-denied dialog); the duplicate log here keeps
   * stack context for tools that lose it across coroutine boundaries.
   */
  override suspend fun syncData(entries: List<PeriodBodyScaleSummary>) {
    val isIntegrated = checkIfAlreadyUsed()
    if (!isIntegrated) {
      AppLog.w(tag, "Health Connect not integrated with current account")
      dialogQueueService.dismissLoader()
      return
    }

    val finalData = entries.flatMap { entry ->
      val timestamp = parseTimestampOrNull(entry.entryTimestamp) ?: return@flatMap emptyList()
      buildBodyScaleData(entry, timestamp)
    }
    try {
      AppLog.d(tag, "Syncing ${finalData.size} entries to Health Connect")
      healthConnect.saveData(finalData)
    } catch (e: Exception) {
      AppLog.e(tag, "User denied health connect permission or save failed", e)
      throw e
    }
  }

  override suspend fun syncEntries(entries: List<Entry>) {
    val isIntegrated = checkIfAlreadyUsed()
    if (!isIntegrated) {
      AppLog.w(tag, "Health Connect not integrated with current account")
      return
    }
    // buildCombinedSyncPayload maps ScaleEntry -> body-scale records and BpmEntry -> blood-pressure
    // (+ resting heart-rate) records, so both weight and BP reach Health Connect on save.
    val finalData = buildCombinedSyncPayload(entries)
    if (finalData.isEmpty()) return
    try {
      AppLog.d(tag, "Syncing ${finalData.size} entry records to Health Connect")
      healthConnect.saveData(finalData)
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to sync entries to Health Connect", e)
      throw e
    }
  }

  // Helper to calculate lean body mass
  private fun calculateLeanBodyMass(weight: Double, bodyFat: Double): Double {
    return weight * (1 - bodyFat / 100)
  }

  /**
   * Builds the list of [HealthConnectData] for a body-scale [summary], applying `> 0.0` guards
   * on every field. Used by both the sync path ([syncData]) and the [ScaleEntry] branch of
   * [deleteEntry] so the two paths cannot drift apart on which fields are meaningful — the
   * previous asymmetry caused HC state drift on zero-valued fields and on a divergent
   * LeanBodyMass formula (delete used `muscleMass`, sync derives from `bodyFat`).
   */
  private fun buildBodyScaleData(
    summary: PeriodBodyScaleSummary,
    timestamp: Instant,
  ): List<HealthConnectData> {
    val data = mutableListOf<HealthConnectData>()
    if (summary.weight > 0.0) {
      data.add(HealthConnectData(DataType.Weight, summary.weight, timeStamp = timestamp))
    }
    summary.bodyFat?.takeIf { it > 0.0 }?.let { bodyFat ->
      data.add(HealthConnectData(DataType.BodyFat, bodyFat, timeStamp = timestamp))
      val leanBodyMass = calculateLeanBodyMass(summary.weight, bodyFat)
      if (leanBodyMass > 0.0) {
        data.add(HealthConnectData(DataType.LeanBodyMass, leanBodyMass, timeStamp = timestamp))
      }
    }
    summary.boneMass?.let { boneMass ->
      val boneMassValue = (boneMass * summary.weight) / 100
      if (boneMassValue > 0.0) {
        data.add(HealthConnectData(DataType.BoneMass, boneMassValue, timeStamp = timestamp))
      }
    }
    summary.bmr?.takeIf { it > 0.0 }?.let { bmr ->
      data.add(HealthConnectData(DataType.BasalMetabolicRate, bmr, timeStamp = timestamp))
    }
    summary.pulse?.takeIf { it > 0.0 }?.let { pulse ->
      data.add(HealthConnectData(DataType.RestingHeartRate, pulse, timeStamp = timestamp))
    }
    return data
  }

  /**
   * Parses an ISO-8601 timestamp, returning null and logging on malformed input
   * so callers can surface a targeted failure instead of a generic exception.
   */
  private fun parseTimestampOrNull(raw: String): Instant? = try {
    Instant.parse(raw)
  } catch (e: DateTimeParseException) {
    // Use AppLog.e so malformed timestamps surface to the crash reporter instead of being
    // hidden as a debug warning — they indicate corrupt data that should be investigated.
    AppLog.e(tag, "Malformed entryTimestamp '$raw' for Health Connect: ${e.message}")
    null
  }

  /**
   * Builds a [BloodPressureRecord]-backed [HealthConnectData] from a [PeriodBpmSummary].
   * Returns null when systolic/diastolic are non-positive so callers can skip the entry.
   * Pulse is **not** part of `BloodPressureRecord`; it is emitted separately as a
   * `HeartRateRecord` ([DataType.RestingHeartRate]) — see [buildBpmData].
   */
  private fun buildBloodPressureData(
    summary: PeriodBpmSummary,
    timestamp: Instant,
  ): HealthConnectData? {
    if (summary.avgSystolic <= 0 || summary.avgDiastolic <= 0) return null
    return HealthConnectData(
      type = DataType.BloodPressure,
      bloodPressure = BloodPressureData(
        systolic = summary.avgSystolic.toDouble(),
        diastolic = summary.avgDiastolic.toDouble(),
      ),
      timeStamp = timestamp,
    )
  }

  /**
   * Builds the list of [HealthConnectData] for a BPM [summary]: the BP record (when valid)
   * plus the pulse [HeartRateRecord] (when `avgPulse > 0`). Used by both [syncBpmData] and
   * the [BpmEntry] branch of [deleteEntry] so save and delete cannot drift on which BPM
   * fields are meaningful.
   */
  private fun buildBpmData(
    summary: PeriodBpmSummary,
    timestamp: Instant,
  ): List<HealthConnectData> {
    val data = mutableListOf<HealthConnectData>()
    buildBloodPressureData(summary, timestamp)?.let { data.add(it) }
    if (summary.avgPulse > 0) {
      data.add(HealthConnectData(DataType.RestingHeartRate, summary.avgPulse.toDouble(), timeStamp = timestamp))
    }
    return data
  }

  override suspend fun deleteEntry(entry: Entry): Boolean {
    return try {
      if (!isLoaded) {
        AppLog.w(tag, "Health Connect service not loaded")
        return false
      }

      // Check if integrated
      val isIntegrated = checkIfAlreadyUsed()
      if (!isIntegrated) {
        AppLog.w(tag, "Health Connect not integrated, skipping deletion")
        return false
      }

      val dataToDelete = mutableListOf<HealthConnectData>()

      when (entry) {
        is BpmEntry -> {
          val summary = entry.toBpmSummary()
          val timestamp = parseTimestampOrNull(summary.entryTimestamp) ?: return false
          val bpmData = buildBpmData(summary, timestamp)
          if (bpmData.isEmpty()) {
            AppLog.w(tag, "BpmEntry has no valid HC data (BP readings invalid and pulse non-positive), cannot delete")
            return false
          }
          dataToDelete.addAll(bpmData)
        }
        is BabyEntry -> {
          AppLog.d(tag, "BabyEntry not synced to HC, skipping")
          return true
        }
        is ScaleEntry -> {
          val summary = entry.toPeriodBodyScaleSummary()
          if (summary == null) {
            AppLog.w(tag, "Could not convert entry to PeriodBodyScaleSummary for deletion")
            return false
          }
          val timestamp = parseTimestampOrNull(summary.entryTimestamp) ?: return false
          dataToDelete.addAll(buildBodyScaleData(summary, timestamp))
        }
      }.let { /* exhaustive */ }

      if (dataToDelete.isEmpty()) {
        AppLog.w(tag, "No data to delete from Health Connect")
        return false
      }

      // Delete data from Health Connect
      val result = healthConnect.deleteEntry(dataToDelete)

      when (result) {
        is HealthConnectResult.Success -> {
          AppLog.i(tag, "Successfully deleted entry from Health Connect")
          true
        }
        is HealthConnectResult.Error -> {
          AppLog.e(tag, "Failed to delete entry from Health Connect", result.toString())
          false
        }
      }
    } catch (e: Exception) {
      AppLog.e(tag, "Exception while deleting entry from Health Connect", e)
      false
    }
  }

  override suspend fun deleteAllData(): Boolean {
    return try {
      if (!isLoaded) {
        AppLog.w(tag, "Health Connect service not loaded")
        return false
      }

      AppLog.i(tag, "Deleting all Health Connect data")
      val result = healthConnect.deleteAllData(requestingPermissions)

      when (result) {
        is HealthConnectResult.Success -> {
          AppLog.i(tag, "Successfully deleted all Health Connect data")
          true
        }

        is HealthConnectResult.Error -> {
          AppLog.e(tag, "Failed to delete Health Connect data", result.toString())
          false
        }
      }
    } catch (e: Exception) {
      AppLog.e(tag, "Exception while deleting Health Connect data", e)
      false
    }
  }

  override suspend fun turnOnIntegration(fromMultiDevice: Boolean, isRequestNeed: Boolean) {
    try {
      val accountId = requireCurrentAccountId()
      val permissionList = getApprovedPermissionList()
      val deviceId = getDeviceId()
      val integration = IntegrationData(
        deviceId = deviceId,
        type = IntegrationType.HEALTH_CONNECT.value,
        preferences = IntegrationPreferences(scopes = permissionList),
      )
      healthConnectRepository.saveIntegration(integration)
      healthConnectRepository.setHealthConnectIntegrationStatus(accountId, true)
      integrationRepository.updateHealthConnectIntegrationOffline(true)
      healthConnectRepository.setHcPermissions(accountId, permissionList)
      // Need to check before.
      integrationRepository.updateLocalAccount()
      true
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to turn on integration", e)
      false
    }

    if (fromMultiDevice) {
      return
    } else if (!isRequestNeed) {
      syncWeightHistory()
    } else {
      syncAllData(isRequestNeed)
    }
  }

  override suspend fun removeHealthConnectIntegration(): Boolean {
    return try {
      val healthConnectStatus = healthConnectStatus()
      if (healthConnectStatus == HealthConnectStatus.INSTALLED ||
        healthConnectStatus == HealthConnectStatus.UPDATE_REQUIRED
      ) {
        // Revoke permissions if Health Connect is available
        revokePermission()
      }
      // Get the device ID for the current integration
      val deviceId = getDeviceId()

      val accountId = currentAccountId
      if (accountId != null) {
        deleteAllData()
        integrationRepository.updateHealthConnectIntegrationOffline(false)
        healthConnectRepository.removeServerHcIntegration(deviceId)
        healthConnectRepository.setHealthConnectIntegrationStatus(accountId, false)
        healthConnectRepository.updateOutOfSync(accountId, false)
        healthConnectRepository.updateAlertSeen(accountId, true)
        delay(500)
        integrationRepository.updateLocalAccount()
      }
      _outOfSyncState.value = false
      dialogQueueService.showToast(Toast.Simple(HealthConnectStrings.ToastStrings.removeHC))
      true
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to remove Health Connect integration", e)
      false
    }
  }

  override suspend fun clearHealthConnect(): Boolean {
    return try {
      val accountId = currentAccountId

      if (accountId == null) {
        AppLog.w(tag, "No active account found")
        return false
      }
      val deviceId = getDeviceId()
      val removingIntegrationData = IntegratedDeviceInfo(
        operationType = IntegrationOperationType.REMOVE.value,
        scopes = IntegrationData(
          deviceId = deviceId,
          type = IntegrationType.HEALTH_CONNECT.value,
        ),
        isCurrentDeviceDeleted = true,
      )
      healthConnectRepository.setStoredIntegrationData(accountId, removingIntegrationData)
      try {
        revokePermission()
        AppLog.i(tag, "Successfully revoked Health Connect permissions")
      } catch (e: Exception) {
        AppLog.w(tag, "Failed to revoke permissions: ${e.message}")
      }
      healthConnectRepository.setHealthConnectIntegrationStatus(accountId, false)
      healthConnectRepository.updateAlertSeen(accountId, false)
      healthConnectRepository.updateOutOfSync(accountId, false)
      healthConnectRepository.updateModalState(accountId, false)
      healthConnectRepository.setOpen(accountId, false)
      deleteAllData()
      AppLog.i(tag, "Health Connect cleared successfully")
      true
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to clear Health Connect", e)
      false
    }
  }

  override suspend fun checkPermissionChange() {
    try {
      val accountId = currentAccountId

      if (accountId == null) {
        AppLog.w(tag, "No active account found")
        return
      }
      val healthConnectData = healthConnectRepository.getAccountByID(accountId)
      val storedGrantedPermissions = healthConnectData?.grantedPermissionList ?: emptyList()
      // Get current granted permissions from Health Connect
      val currentGrantedPermissions = getApprovedPermissionList()
      if (storedGrantedPermissions.isEmpty()) {
        AppLog.i(tag, "No stored permissions found, treating as initial setup")
        return
      } else {
        AppLog.i(tag, "Permissions found, checking for changes")
        if (storedGrantedPermissions.toSet() != currentGrantedPermissions.toSet()) {
          AppLog.i(tag, "Permission changes detected, updating integration")
          healthConnectRepository.setHcPermissions(accountId, currentGrantedPermissions)
        }
        turnOnIntegration(fromMultiDevice = true, isRequestNeed = true)
      }
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to check permission changes", e)
    }
  }

  /**
   * Checks for multiple device connections.
   * Similar to Angular's checkMultiDeviceConnection implementation.
   * First checks if multiple device IDs exist using checkMultipleDeviceIds.
   * If true, sets up the integration and handles out-of-sync state.
   *
   * @param isPermissionEnabled Whether permissions are currently enabled
   * @return true if multiple device connection detected and handled, false otherwise
   */
  override suspend fun checkMultiDeviceConnection(isPermissionEnabled: Boolean): Boolean {
    return try {
      val accountId = currentAccountId

      if (accountId == null) {
        AppLog.w(tag, "No active account found")
        return false
      }

      // Check if multiple device IDs exist (similar to Angular)
      val isMultipleDeviceIds = checkMultipleDeviceIds(IntegrationType.HEALTH_CONNECT)
      if (isMultipleDeviceIds) {
        AppLog.i(tag, "Multiple device IDs detected - setting up integration")

        // Set Health Connect integration status to true
        integrationRepository.updateHealthConnectIntegrationOffline(true)
        healthConnectRepository.setHealthConnectIntegrationStatus(accountId, true)
        dialogQueueService.showDialog(
          DialogModel.Custom(
            contentKey = DialogType.MultipleDeviceConnection,
            onConfirm = {
              dialogQueueService.dismissCurrent()
              onConfirmMultiDevice(accountId)
            },
            onDismiss = {
              onCancelMultiDevice(accountId)
            },
          ),
        )
        // Set out of sync state if permissions are not enabled
        if (!isPermissionEnabled) {
          _outOfSyncState.value = true
          healthConnectRepository.updateOutOfSync(accountId, true)
        }

        // Turn on integration for multi-device scenario
        turnOnIntegration(fromMultiDevice = true, isRequestNeed = true)
        return true
      }

      // Return the result (false if no multiple device IDs detected)
      return isMultipleDeviceIds
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to check multiple device connection", e)
      false
    }
  }

  private fun onConfirmMultiDevice(accountId: String){
    appScope.launch(Dispatchers.Main) {
      appNavigationService.navigateTo(AppRoute.Integration.IntegrationList)
      appNavigationService.navigateTo(AppRoute.Integration.HealthConnect)
      healthConnectRepository.updateOutOfSync(accountId, false)
      healthConnectRepository.updateModalState(accountId, true)
    }
  }

  private fun onCancelMultiDevice(accountId: String){
    appScope.launch(Dispatchers.Main) {
      healthConnectRepository.setHealthConnectIntegrationStatus(accountId, true)
      healthConnectRepository.updateOutOfSync(accountId, true)
      healthConnectRepository.updateModalState(accountId, true)
      _outOfSyncState.value = true
    }
  }

  /**
   * Checks if multiple device IDs exist for the given integration type.
   * Similar to Angular's checkMultipleDeviceIds implementation.
   * Returns true if stored integration data is null/undefined and the integration is on.
   *
   * @param integrations The integration type to check (HEALTH_CONNECT or HEALTH_KIT)
   * @return true if stored integration is null and integration is on, false otherwise
   */
  override suspend fun checkMultipleDeviceIds(integrations: IntegrationType): Boolean {
    return try {
      val accountId = currentAccountId
      if (accountId == null) {
        AppLog.w(tag, "No active account found for checkMultipleDeviceIds")
        return false
      }

      // Get stored integration data (local: no deviceId = this device hasn't registered yet)
      val localIntegration = healthConnectRepository.getStoredIntegrationData(accountId)
      // Get integration status from server
      val integrationsFromServer = integrationRepository.integrationsFromServer.first()
      val isIntegrationOn =  integrationsFromServer?.isHealthConnectOn ?: false
      AppLog.d(tag, "checkMultipleDeviceIds for $integrations: storedIntegration=${localIntegration}")
      // Return true if stored integration is null AND integration is on
      val result = ( localIntegration == null || localIntegration.scopes.deviceId.isEmpty()) && isIntegrationOn
      AppLog.d(tag, "checkMultipleDeviceIds for $integrations: storedIntegration=${localIntegration}, isIntegrationOn=$isIntegrationOn, result=$result")
      result
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to check multiple device IDs for $integrations", e)
      false
    }
  }

  /**
   * Gets the list of approved permissions from Health Connect.
   * This method returns the permissions that the user has granted to the app.
   */
  override suspend fun getApprovedPermissionList(): List<String> {
    return try {
      if (!isLoaded) {
        AppLog.w(tag, "Health Connect service not loaded")
        return emptyList()
      }

      val approvedPermissions = healthConnect.getApprovedPermissionList()
      AppLog.i(tag, "Retrieved ${approvedPermissions.size} approved permissions")
      approvedPermissions.toList()
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to get approved permission list", e)
      emptyList()
    }
  }

  override fun syncWeightHistory() {
    dialogQueueService.showDialog(
      DialogModel.Confirm(
        title = HealthConnectStrings.SyncAlert.title,
        message = HealthConnectStrings.SyncAlert.description,
        confirmText = HealthConnectStrings.ActionButtons.sync,
        cancelText = HealthConnectStrings.ActionButtons.cancel,
        onConfirm = {
          dialogQueueService.showLoader(HealthConnectStrings.Loader.loading)
          appScope.launch {
            syncAllData()
            dialogQueueService.dismissCurrent()
            dialogQueueService.dismissLoader()
            dialogQueueService.showToast(Toast.Simple(HealthConnectStrings.ToastStrings.syncToast))
          }
        },
        onCancel = {
          dialogQueueService.dismissCurrent()
        },
        onDismiss = {
          dialogQueueService.dismissCurrent()
        },
      ),
    )
  }

  override suspend fun healthConnectOutOfSync(): Boolean {
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
        _outOfSyncState.value = true
        healthConnectRepository.updateOutOfSync(accountId, true)
        healthConnectRepository.updateModalState(accountId, true)
        AppLog.i(tag, "Health Connect permissions disabled - marked as out of sync")
        true
      }

      HealthConnectPermissionStatus.ALL,
      HealthConnectPermissionStatus.PARTIAL -> {
        _outOfSyncState.value = false
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
  override suspend fun checkHealthConnectPermissionDisabled() {
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
    _outOfSyncState.value = true // Set observable for out of sync
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
              _outOfSyncState.value = true
            }
          },
          onDismiss = {
            appScope.launch {
              healthConnectRepository.setHealthConnectIntegrationStatus(accountId, true)
              healthConnectRepository.updateOutOfSync(accountId, true)
              healthConnectRepository.updateModalState(accountId, true)
              _outOfSyncState.value = true
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
      AppLog.w(tag, "[checkHealthConnectPermissionDisabled] checkIfAlreadyUsed threw")
      false
    }
    AppLog.d(tag, "[checkHealthConnectPermissionDisabled] isAlreadyConnected=$isAlreadyConnected")
    if (!isAlreadyConnected) {
      AppLog.d(tag, "[checkHealthConnectPermissionDisabled] RETURN: !isAlreadyConnected, setting _outOfSyncState=$outOfSyncSession")
      _outOfSyncState.value = outOfSyncSession
      return
    }
    val shouldShowPopup = true
    if (isLocallyIntegrated) {
      //for migration
      AppLog.d(tag, "[checkHealthConnectPermissionDisabled] isLocallyIntegrated=true, calling turnOnIntegration")
      turnOnIntegration(fromMultiDevice = true, isRequestNeed = true)
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
      _outOfSyncState.value = false
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
