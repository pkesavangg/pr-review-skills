package com.dmdbrands.gurus.weight.app.viewmodel

import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BpmEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.services.IAccountFlagService
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper
import com.dmdbrands.gurus.weight.features.common.model.ReadingToast
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.toBpmEntry
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.toScaleEntry
import com.dmdbrands.library.ggbluetooth.model.GGBPMEntry
import com.dmdbrands.library.ggbluetooth.model.GGEntry
import com.dmdbrands.library.ggbluetooth.model.GGScaleEntry
import com.dmdbrands.library.ggbluetooth.model.GGScanResponse
import com.dmdbrands.library.ggbluetooth.model.GGWeightEntry
import com.dmdbrands.library.ggbluetooth.enums.GGScanResponseType
import com.greatergoods.ggbluetoothsdk.external.enums.GGDeviceProtocolType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Owns the entry-routing / entry-save / BPM-reading slice of [AppViewModel] (MOB-1500).
 * Behaviour-preserving verbatim move of the device-callback entry routing, weight & BPM save paths,
 * BMI back-fill, and the BPM arrival card. The weight/baby reading card and baby assignment live in
 * [ReadingAssignmentManager] (reached via [onShowReadingToast]). Base-class lateinit services are
 * reached through lazy provider lambdas so their value is never captured before injection.
 */
class EntrySaveManager(
  private val scope: CoroutineScope,
  private val getCurrentAccountId: () -> String?,
  private val provideNavigation: () -> IAppNavigationService,
  private val provideDialogQueue: () -> IDialogQueueService,
  private val deviceService: IDeviceService,
  private val entryService: IEntryService,
  private val accountService: IAccountService,
  private val accountFlagService: IAccountFlagService,
  private val onShowReadingToast: (List<ScaleEntry>, ProductType, String?) -> Unit,
) {

  private val TAG = "AppViewModel"

  private val navigationService: IAppNavigationService get() = provideNavigation()
  private val dialogQueueService: IDialogQueueService get() = provideDialogQueue()

  fun handleEntryResponse(entryResponse: GGScanResponse.Entry) {
    val data = entryResponse.data
    val scaleEntries = data.filterIsInstance<GGScaleEntry>()
    val bpmEntries = data.filterIsInstance<GGBPMEntry>()
    // Weight-only devices (baby scale + weight-only scales) emit GGWeightEntry, which carries
    // no body composition — distinct from the body-scale GGScaleEntry (MOB-598).
    val weightEntries = data.filterIsInstance<GGWeightEntry>()

    // Confirms the scale actually emitted a reading and which GGEntry subtype reached the app —
    // the missing log when a baby reading "doesn't sync" (it never arrived / wasn't a handled type).
    AppLog.i(
      TAG,
      "handleEntryResponse type=${entryResponse.type} total=${data.size} " +
        "scale=${scaleEntries.size} bpm=${bpmEntries.size} weight=${weightEntries.size} " +
        "subtypes=${data.map { it.javaClass.simpleName }}",
    )

    when (entryResponse.type) {
      GGScanResponseType.SINGLE_ENTRY, GGScanResponseType.MULTI_ENTRIES -> {
        if (scaleEntries.isNotEmpty()) {
          saveEntry(scaleEntries)
        }
        if (bpmEntries.isNotEmpty()) {
          saveBpmEntry(bpmEntries)
        }
        // Route weight-only readings through the same save/assign path by representing each
        // as a weight-only scale entry. saveEntry's SKU check then sends a baby-scale reading
        // into the assign-to-baby flow; only the weight is used downstream (MOB-598).
        if (weightEntries.isNotEmpty()) {
          saveEntry(weightEntries.map { it.toWeightOnlyScaleEntry() })
        }
      }

      else ->
        AppLog.w(TAG, "handleEntryResponse: unhandled entry type=${entryResponse.type}")
    }
  }

  /**
   * Represents a weight-only [GGWeightEntry] (baby scale / weight-only scale) as a body-scale
   * [GGScaleEntry] with zeroed body-composition so it can flow through the shared [saveEntry]
   * path. Only the weight is meaningful; the baby-assignment flow (and `toBabyEntry`) reads the
   * weight alone, so the zeroed metrics are never persisted (MOB-598).
   */
  private fun GGWeightEntry.toWeightOnlyScaleEntry(): GGScaleEntry =
    GGScaleEntry(
      bmi = 0f,
      bmr = 0,
      bodyFat = 0f,
      water = 0f,
      boneMass = 0f,
      metabolicAge = 0,
      muscleMass = 0f,
      proteinPercent = 0f,
      skeletalMusclePercent = 0f,
      subcutaneousFatPercent = 0f,
      unit = unit,
      visceralFatLevel = 0,
      weight = weight,
      weightInKg = weightInKg ?: weight,
      date = date,
      impedance = 0f,
      pulse = 0,
      broadcastId = broadcastId,
      broadcastIdString = broadcastIdString,
      protocolType = protocolType,
      operationType = operationType,
    )

  fun saveBpmEntry(ggEntries: List<GGBPMEntry>) {
    // A monitor holds multiple user slots; attribute the reading to the row matching this slot.
    val userNumber = ggEntries.firstOrNull()?.userNumber?.toInt()
    val protocolType = ggEntries.firstOrNull()?.protocolType
    saveBluetoothEntries(ggEntries, userNumber, protocolType) { accountId, deviceId ->
      ggEntries.mapIndexed { index, entry -> entry.toBpmEntry(accountId, deviceId, index.toLong()) }
    }
  }

  private fun <T : GGEntry> saveBluetoothEntries(
    ggEntries: List<T>,
    userNumber: Int? = null,
    protocolType: String? = null,
    mapEntries: suspend (accountId: String, deviceId: String) -> List<Entry>,
  ) {
    scope.launch {
      if (ggEntries.isEmpty()) return@launch
      val accountId = getCurrentAccountId() ?: return@launch
      val isSetupInProgress = deviceService.isSetupInProgress()
      val broadcastId = ggEntries.first().broadcastId
      // Match the reading to the exact paired row by broadcastId + userNumber (a monitor can be
      // paired under multiple user slots). Fall back to broadcastId-only, then to the single-device
      // heal: a device synced from GET /v3/paired-device carries no broadcastId (the server omits
      // it), so we attribute to the single un-identified BPM device and backfill it. (MOB-596)
      val device =
        (userNumber?.let { deviceService.getScaleByBroadcastIdAndUser(broadcastId, it, accountId) })
          ?: deviceService.getScaleByBroadcastId(broadcastId, accountId)
          ?: deviceService.healBpmDeviceBroadcastId(broadcastId, accountId, protocolType)

      if (device == null && !isSetupInProgress) return@launch

      try {
        val entries = mapEntries(accountId, device?.id ?: "")
        if (isSetupInProgress) {
          // During setup, save immediately without a reading card (parity with the weight path).
          entryService.addEntry(entries)
          checkAccountFlags("entry")
        } else {
          // Show the "New BPM Reading Received" card with SAVE/DISCARD; the reading is persisted
          // only when the user taps SAVE — no auto-save, matching the weight-scale flow.
          showBpmReadingToast(entries)
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error saving entry", e)
      }
    }
  }

  /**
   * Shows the "New BPM Reading Received" arrival card (SAVE/DISCARD) for a synced monitor reading,
   * mirroring the weight-scale flow: the reading is only persisted on SAVE; DISCARD just dismisses
   * (nothing was written). Extra buffered readings surface a "+N more… VIEW" pill and all save
   * together on SAVE.
   */
  private fun showBpmReadingToast(entries: List<Entry>) {
    val latest = entries.filterIsInstance<BpmEntry>().maxByOrNull { it.entry.entryTimestamp } ?: return
    val additionalCount = (entries.size - 1).coerceAtLeast(0)
    dialogQueueService.showToast(
      Toast.Custom(
        ReadingToast(
          reading = "${latest.systolic}/${latest.diastolic} mmhg ${latest.pulse} pulse",
          type = ProductType.BLOOD_PRESSURE,
          timestamp = "Just now",
          additionalCount = additionalCount,
          primaryAction = { saveBpmEntriesFromToast(entries) },
          secondaryAction = {
            // The reading is only persisted on SAVE, so discarding just dismisses the card (MOB-598).
            dialogQueueService.dismissToast()
            AppLog.i(TAG, "BPM entry discarded via reading toast")
          },
          onView = {
            scope.launch { navigationService.navigateTo(AppRoute.Main.History) }
          },
        ),
      ),
    )
  }

  /** Persists the buffered BPM readings from the reading toast's SAVE action. */
  private fun saveBpmEntriesFromToast(entries: List<Entry>) {
    scope.launch {
      try {
        entryService.addEntry(entries)
        checkAccountFlags("entry")
        AppLog.i(TAG, "BPM entry saved via reading toast")
      } catch (e: Exception) {
        AppLog.e(TAG, "Error saving BPM entry from toast", e)
      }
    }
  }

  fun saveEntry(ggEntry: List<GGScaleEntry>) {
    scope.launch {
      if (ggEntry.isEmpty()) {
        return@launch
      }
      val accountId = getCurrentAccountId() ?: return@launch
      // During setup scale list will be empty so ignoring this check during setup and allow all entries.
      val isSetupInProgress = deviceService.isSetupInProgress()
      val readingBroadcastId = ggEntry.first().broadcastId
      // A6 baby scales are often saved without a broadcastId (server omits it / older rows), so the
      // id lookup misses and the reading gets misclassified as a WEIGHT reading — surfacing a
      // "weight reading received" toast and saving it as weight even when it's a baby scale with no
      // baby profile. Fall back to attributing an A6 reading to the lone paired baby scale (by SKU)
      // so it classifies as BABY. (baby-scale reconnect fix)
      val readingIsA6 = ggEntry.first().protocolType == GGDeviceProtocolType.GG_DEVICE_PROTOCOL_A6.value
      val device =
        deviceService.getScaleByBroadcastId(readingBroadcastId, accountId)
          ?: if (readingIsA6) deviceService.healBabyScaleBroadcastId(readingBroadcastId, accountId) else null

      // A reading whose broadcastId matches no paired device (and isn't an A6 baby scale healed
      // above) must not be saved/toasted under this account — mirrors the BPM path's guard.
      if (device == null && !isSetupInProgress) {
        return@launch
      }

      // Get user height for BMI calculation
      val activeAccount = accountService.activeAccountFlow.first()
      val userHeight = activeAccount?.height
      // Store the reading in the My Weight (adult) unit preference — account.isMetric is
      // weightUnit == KG. NOT measurementUnits, which is the baby-scale unit. So an A6/0382
      // reading (always broadcast in kg) is saved as the lb value the scale displays for
      // imperial (lb) accounts, and as kg for metric accounts. (MOB-872)
      val isMetric = activeAccount?.isMetric == true

      val entry = buildScaleEntriesWithBmi(ggEntry, accountId, device?.id ?: "", isMetric, userHeight)

      if (isSetupInProgress) {
        // During setup, save immediately without toast
        try {
          entryService.addEntry(entry)
          checkAccountFlags("entry")
        } catch (e: Exception) {
          AppLog.e(TAG, "Error during saving entry", e)
        }
      } else {
        // Show reading toast — user decides to save or discard
        onShowReadingToast(entry, resolveReadingType(device), device?.sku)
      }
    }
  }

  /**
   * Maps raw [GGScaleEntry] readings to [ScaleEntry]s, back-filling BMI from [userHeight] when the
   * reading carried none (0.0/null). Behaviour extracted verbatim from [saveEntry].
   */
  private fun buildScaleEntriesWithBmi(
    ggEntry: List<GGScaleEntry>,
    accountId: String,
    deviceId: String,
    isMetric: Boolean,
    userHeight: Int?,
  ): List<ScaleEntry> =
    ggEntry.map { ggScaleEntry ->
      val scaleEntry = ggScaleEntry.toScaleEntry(accountId, deviceId, isMetric)

      // Check if BMI is 0.0 or null and calculate it if user height is available
      if ((scaleEntry.scale.scaleEntry.bmi == null || scaleEntry.scale.scaleEntry.bmi == 0.0) &&
        userHeight != null
      ) {
        val calculatedBmi =
          EntryHelper.getCalculatedBMI(
            weight =
              scaleEntry.scale.scaleEntry.weight
                .toFloat(),
            unit = scaleEntry.entry.unit,
            height = userHeight,
          )

        // Update the BMI in the scale entry
        val updatedScaleEntry = scaleEntry.scale.scaleEntry.copy(bmi = calculatedBmi)
        val updatedScaleEntryWithMetrics = scaleEntry.scale.copy(scaleEntry = updatedScaleEntry)

        AppLog.d(
          TAG,
          "Calculated BMI: $calculatedBmi for weight: ${scaleEntry.scale.scaleEntry.weight}, height: $userHeight",
        )

        scaleEntry.copy(scale = updatedScaleEntryWithMetrics)
      } else {
        scaleEntry
      }
    }

  /** Classifies a reading's [ProductType] from the originating device SKU (weight by default). */
  private fun resolveReadingType(device: Device?): ProductType =
    device?.sku?.let { sku ->
      when {
        DeviceHelper.isBabyScale(sku) -> ProductType.BABY
        DeviceHelper.isBpmDevice(sku) -> ProductType.BLOOD_PRESSURE
        else -> ProductType.MY_WEIGHT
      }
    } ?: ProductType.MY_WEIGHT

  // * Checks for account flags and triggers appropriate actions.
  // * @param trigger The trigger type (e.g., "login", "entry")
  // */
  fun checkAccountFlags(trigger: String) {
    scope.launch {
      try {
        // First get the account flag
        val accountFlag = accountFlagService.getAccountFlag()
        if (accountFlag != null) {
          AppLog.d(TAG, "Found account flag: ${accountFlag.type} for trigger: $trigger")
          // Check if the flag should be triggered
          accountFlagService.checkAccountFlag(trigger)
        } else {
          AppLog.d(TAG, "No account flags found for trigger: $trigger")
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to check account flags for trigger: $trigger", e.toString())
      }
    }
  }
}
