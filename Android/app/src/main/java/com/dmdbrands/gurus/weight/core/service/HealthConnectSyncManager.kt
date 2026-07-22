package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BabyEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BpmEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBpmSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.toBpmSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.toPeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.repository.IEntryRepository
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.integration.strings.HealthConnectStrings
import com.greatergoods.libs.healthconnect.HealthConnect
import com.greatergoods.libs.healthconnect.enums.DataType
import com.greatergoods.libs.healthconnect.model.BloodPressureData
import com.greatergoods.libs.healthconnect.model.HealthConnectData
import com.greatergoods.libs.healthconnect.model.HealthConnectOptions
import com.greatergoods.libs.healthconnect.model.HealthConnectResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Sync / read-write engine slice extracted from [HealthConnectService] (MOB-1500). Owns building
 * the HealthConnect payload from entries and pushing/deleting it via the library
 * ([syncAllData] / [syncData] / [syncEntries] / [deleteEntry] / [deleteAllData]). Cross-cutting
 * checks (integration ownership, active account) and shared mutable state (the HealthConnect
 * instance, the loaded flag) are supplied by [HealthConnectService] via callbacks/getters.
 * Behaviour-preserving verbatim move.
 */
class HealthConnectSyncManager(
  private val getHealthConnect: () -> HealthConnect,
  private val getIsLoaded: () -> Boolean,
  private val requestingPermissions: HealthConnectOptions,
  private val hasActiveAccount: () -> Boolean,
  private val requireCurrentAccountId: () -> String,
  private val checkIfAlreadyUsed: suspend () -> Boolean,
  private val dialogQueueService: IDialogQueueService,
  private val entryRepository: IEntryRepository,
  private val appScope: CoroutineScope,
) {

  private val tag = "HealthConnectService"
  private val healthConnect: HealthConnect get() = getHealthConnect()
  private val isLoaded: Boolean get() = getIsLoaded()

  suspend fun syncAllData(fromOutOfSync: Boolean = false): Boolean {
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
  suspend fun syncData(entries: List<PeriodBodyScaleSummary>) {
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

  suspend fun syncEntries(entries: List<Entry>) {
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

  suspend fun deleteEntry(entry: Entry): Boolean {
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

  suspend fun deleteAllData(): Boolean {
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

  fun syncWeightHistory() {
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
}
