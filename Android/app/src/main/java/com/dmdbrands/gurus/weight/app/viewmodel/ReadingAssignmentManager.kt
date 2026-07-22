package com.dmdbrands.gurus.weight.app.viewmodel

import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.services.OperationType
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BabyEntryEntity
import com.dmdbrands.gurus.weight.domain.enums.BabyEntryType
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BabyEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.services.IProductSelectionManager
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.ReadingToast
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.strings.ReadingToastStrings
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.formatWeightValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Owns the reading-notification / baby-assignment slice of [AppViewModel] (MOB-1500).
 * Behaviour-preserving verbatim move of the weight/baby reading card, the assign-to-baby picker,
 * the held-reading Add-a-Baby handoff, and the post-assignment card. Entry-save side effects run
 * through [onCheckAccountFlags] (owned by [EntrySaveManager]); [saveEntry] triggers this slice via
 * [EntrySaveManager.onShowReadingToast]. Base-class lateinit services are reached through lazy
 * provider lambdas so their value is never captured before injection.
 */
class ReadingAssignmentManager(
  private val scope: CoroutineScope,
  private val getCurrentAccountId: () -> String?,
  private val provideNavigation: () -> IAppNavigationService,
  private val provideDialogQueue: () -> IDialogQueueService,
  private val provideProductSelection: () -> IProductSelectionManager,
  private val entryService: IEntryService,
  private val onCheckAccountFlags: (String) -> Unit,
) {

  private val TAG = "AppViewModel"

  private val navigationService: IAppNavigationService get() = provideNavigation()
  private val dialogQueueService: IDialogQueueService get() = provideDialogQueue()
  private val productSelectionManager: IProductSelectionManager get() = provideProductSelection()

  fun showReadingToast(
    entry: List<ScaleEntry>,
    readingType: ProductType,
    sourceSku: String?,
  ) {
    // Show the latest reading in the card; any extra buffered readings (taken while
    // disconnected) surface as a "+N more… VIEW" count pill (MOB-598).
    val latestEntry = entry.maxByOrNull { it.entry.entryTimestamp } ?: return
    val reading = formatReadingForDisplay(latestEntry, readingType)
    val additionalCount = (entry.size - 1).coerceAtLeast(0)

    // Snapshot of baby profiles at arrival — drives the single-baby card and timeout auto-assign.
    val babiesAtArrival = if (readingType == ProductType.BABY) availableBabyProfiles() else emptyList()
    val singleBabyName = babiesAtArrival.singleOrNull()?.name

    // A baby scale reading with no baby profile has nowhere to be saved —
    // surface an "ADD A BABY" CTA instead of the assign flow (MOB-426).
    val hasNoBabyProfile = readingType == ProductType.BABY && babiesAtArrival.isEmpty()

    // Multi-baby readings auto-assign to the last-assigned baby on timeout, if it still
    // exists; single-baby/no-baby readings have no auto-assign target (MOB-598).
    val autoAssignBabyId =
      lastAssignedBabyId
        ?.takeIf { readingType == ProductType.BABY && babiesAtArrival.size > 1 }
        ?.takeIf { id -> babiesAtArrival.any { it.id == id } }

    dialogQueueService.showToast(
      Toast.Custom(
        ReadingToast(
          reading = reading,
          type = readingType,
          timestamp = "Just now",
          noBabyProfile = hasNoBabyProfile,
          assignTargetName = singleBabyName,
          additionalCount = additionalCount,
          primaryAction = buildReadingToastPrimaryAction(
            reading, entry, sourceSku, readingType, babiesAtArrival, hasNoBabyProfile,
          ),
          secondaryAction = {
            // The reading is only persisted on Save/Assign, so discarding an unsynced
            // reading just dismisses the card — nothing was written (MOB-428). Also drop any
            // held reading so a later baby-add never picks it up.
            pendingBabyReading = null
            dialogQueueService.dismissToast()
            AppLog.i(TAG, "Entry discarded via reading toast")
          },
          onView = {
            // "VIEW" opens History so all buffered readings can be seen (MOB-598).
            scope.launch { navigationService.navigateTo(AppRoute.Main.History) }
          },
          // Timeout = no user response → KEEP the reading (per Figma "auto-assign on timeout"),
          // NOT discard: weight/BPM auto-save, baby auto-assigns to its target. Only the no-baby
          // and multi-baby-without-a-target cases have nowhere to save, so they just dismiss.
          onTimeout = buildReadingToastTimeout(
            reading, entry, sourceSku, readingType, babiesAtArrival, hasNoBabyProfile, autoAssignBabyId,
          ),
        ),
      ),
    )
  }

  /** Builds the reading toast's SAVE / assign / add-baby primary action. */
  private fun buildReadingToastPrimaryAction(
    reading: String,
    entry: List<ScaleEntry>,
    sourceSku: String?,
    readingType: ProductType,
    babiesAtArrival: List<BabyProfile>,
    hasNoBabyProfile: Boolean,
  ): () -> Unit = {
    if (hasNoBabyProfile) {
      // Hold the reading and auto-assign it to the baby the user is about to create; the
      // deactivate handler assigns on success or drops it on cancel (Option A).
      pendingBabyReading =
        PendingBabyReading(
          reading = reading,
          entry = entry,
          sourceSku = sourceSku,
          baselineBabyIds = babiesAtArrival.map { it.id }.toSet(),
        )
      scope.launch {
        registerAddBabyDeactivateHandler()
        navigationService.navigateTo(AppRoute.AccountSettings.AddBaby())
      }
    } else if (readingType == ProductType.BABY) {
      if (babiesAtArrival.size == 1) {
        // Single baby — SAVE persists straight to that baby (no picker) (MOB-598).
        scope.launch {
          assignReadingToBaby(
            reading,
            entry,
            babiesAtArrival.first().id,
            babiesAtArrival,
            emptyList(),
            sourceSku,
          )
        }
      } else {
        showAssignMeasurementDialog(reading, entry, sourceSku = sourceSku)
      }
    } else {
      saveEntryFromToast(entry)
    }
  }

  /**
   * Builds the reading toast's timeout action: KEEP the reading (auto-save / auto-assign) except
   * the no-baby and multi-baby-without-target cases, which have nowhere to save (return null).
   */
  private fun buildReadingToastTimeout(
    reading: String,
    entry: List<ScaleEntry>,
    sourceSku: String?,
    readingType: ProductType,
    babiesAtArrival: List<BabyProfile>,
    hasNoBabyProfile: Boolean,
    autoAssignBabyId: String?,
  ): (() -> Unit)? =
    when {
      hasNoBabyProfile -> null
      autoAssignBabyId != null -> {
        val babyId = autoAssignBabyId
        {
          scope.launch {
            assignReadingToBaby(
              reading,
              entry,
              babyId,
              babiesAtArrival,
              emptyList(),
              sourceSku,
            )
          }
        }
      }
      readingType == ProductType.BABY && babiesAtArrival.size == 1 -> {
        val babyId = babiesAtArrival.first().id
        {
          scope.launch {
            assignReadingToBaby(
              reading,
              entry,
              babyId,
              babiesAtArrival,
              emptyList(),
              sourceSku,
            )
          }
        }
      }
      readingType == ProductType.BABY -> null
      else -> {
        { saveEntryFromToast(entry) }
      }
    }

  /** Saves a non-baby reading straight from the reading toast's SAVE action. */
  private fun saveEntryFromToast(entry: List<ScaleEntry>) {
    scope.launch {
      try {
        entryService.addEntry(entry)
        onCheckAccountFlags("entry")
        AppLog.i(TAG, "Entry saved via reading toast")
      } catch (e: Exception) {
        AppLog.e(TAG, "Error saving entry from toast", e)
      }
    }
  }

  private fun availableBabyProfiles(): List<BabyProfile> =
    productSelectionManager.availableProducts.value
      .filterIsInstance<ProductSelection.Baby>()
      .map { it.profile }

  /** Most-recently-assigned baby; the timeout auto-assign target for multi-baby readings (MOB-598). */
  private var lastAssignedBabyId: String? = null

  /**
   * A baby-scale reading held while the user creates a baby from the no-baby toast's "ADD A BABY".
   * [baselineBabyIds] is the set of baby ids that existed when the reading arrived, so we can detect
   * the one newly-created baby to auto-assign it to.
   */
  private data class PendingBabyReading(
    val reading: String,
    val entry: List<ScaleEntry>,
    val sourceSku: String?,
    val baselineBabyIds: Set<String>,
  )

  private var pendingBabyReading: PendingBabyReading? = null

  /**
   * Registers a one-shot handler that fires when the user leaves the Add-a-Baby screen after tapping
   * "ADD A BABY" on a no-baby reading toast. If a new baby was created, the held reading is
   * auto-assigned to it (Option A); if the user cancelled (no new baby), the pending reading is
   * dropped so it can never latch onto an unrelated future baby.
   */
  private fun registerAddBabyDeactivateHandler() {
    scope.launch {
      navigationService.registerOnDeactivate(AppRoute.AccountSettings.AddBaby()) {
        val pending = pendingBabyReading
        pendingBabyReading = null
        if (pending != null) {
          val newBaby = availableBabyProfiles().firstOrNull { it.id !in pending.baselineBabyIds }
          if (newBaby != null) {
            // A baby now exists — re-present the reading as the ASSIGN / DON'T ASSIGN card so the
            // user chooses (or it auto-assigns on timeout). Don't silently auto-assign and show the
            // "Reading assigned / Assign to new baby" post card. (Figma 30295-24866)
            AppLog.i(TAG, "Re-presenting held reading now that baby ${newBaby.id} exists")
            showReadingToast(pending.entry, ProductType.BABY, pending.sourceSku)
          } else {
            AppLog.i(TAG, "Add-a-baby cancelled — dropping held reading")
          }
        }
        navigationService.unregisterOnDeactivate(AppRoute.AccountSettings.AddBaby())
        // Never block leaving the screen.
        true
      }
    }
  }

  /**
   * Shows the baby picker. On confirm, assigns the reading to the chosen baby.
   * [preSelectedBabyId] pre-selects a baby (used by Reassign); [previousEntryIds] are the
   * locally-saved entries to remove first when reassigning, so a reassign never duplicates.
   */
  fun showAssignMeasurementDialog(
    reading: String,
    entry: List<ScaleEntry>,
    preSelectedBabyId: String? = null,
    previousEntryIds: List<Long> = emptyList(),
    sourceSku: String? = null,
  ) {
    val babies = availableBabyProfiles()
    dialogQueueService.showDialog(
      DialogModel.Custom(
        contentKey = DialogType.AssignMeasurement,
        params =
          buildMap {
            put("babies", babies)
            put("reading", reading)
            put("timestamp", "Just now")
            preSelectedBabyId?.let { put("preSelectedBabyId", it) }
            // "Assign to new baby" row → leave the picker for the Add-a-Baby flow (MOB-598).
            put(
              "onAssignNewBaby",
              { scope.launch { navigationService.navigateTo(AppRoute.AccountSettings.AddBaby()) } },
            )
          },
        onConfirm = { result ->
          val babyId = result as? String ?: return@Custom
          scope.launch {
            assignReadingToBaby(reading, entry, babyId, babies, previousEntryIds, sourceSku)
          }
        },
        dismissOnBackPress = true,
        dismissOnClickOutside = true,
      ),
    )
  }

  /**
   * Persists the reading to the selected baby (synced to /v3/entries, category=baby — §2.16) and
   * surfaces the post-assignment card with a Reassign affordance. When reassigning, the entries
   * from the previously chosen baby are deleted first so the reading lands on exactly one baby.
   */
  suspend fun assignReadingToBaby(
    reading: String,
    entry: List<ScaleEntry>,
    babyId: String,
    babies: List<BabyProfile>,
    previousEntryIds: List<Long>,
    sourceSku: String? = null,
  ) {
    try {
      val accountId = getCurrentAccountId() ?: return
      // Persist to the new baby first; addBabyEntry returns -1 on a null account or a swallowed
      // DB-insert exception. Bail before touching the previous baby's entries or claiming success,
      // so a failed write never surfaces as "Reading assigned to X" (and a later Reassign never
      // deletes a bogus -1 id, which would leave a duplicate behind).
      // One batched insert + a SINGLE server sync for all buffered readings (not one full sync per
      // reading) — assigning K readings is one round-trip. (MOB-598 PR #2130)
      val savedIds = entryService.addBabyEntries(entry.map { it.toBabyEntry(babyId, accountId, sourceSku) })
      if (savedIds.size != entry.size || savedIds.any { it <= 0L }) {
        AppLog.e(TAG, "Baby entry save failed for $babyId (savedIds=$savedIds)")
        dialogQueueService.showToast(Toast.Simple(message = ReadingToastStrings.SaveFailed))
        return
      }
      // Save succeeded — now it's safe to remove the entries from the previously chosen baby.
      // deleteBabyEntry syncs the deletion to the server (operationType=delete), so a reassign
      // never leaves the reading on both babies locally or server-side.
      previousEntryIds.forEach { entryService.deleteBabyEntry(it) }
      // Remember the target so a later multi-baby reading can auto-assign here on timeout (MOB-598).
      lastAssignedBabyId = babyId
      onCheckAccountFlags("entry")
      AppLog.i(TAG, "Baby entry assigned to $babyId")
      babies.firstOrNull { it.id == babyId }?.let { baby ->
        // No other baby in this snapshot → the post-assign card offers "Assign to new baby".
        val noOtherBaby = babies.none { it.id != babyId }
        showBabyAssignedToast(reading, entry, baby, savedIds, sourceSku, noOtherBaby)
      }
    } catch (e: Exception) {
      AppLog.e(TAG, "Error saving baby entry", e)
    }
  }

  /**
   * Post-assignment card ("Reading assigned to X"). Its action re-opens the picker for Reassign,
   * or — when this is the only baby, so there's nothing to reassign to — becomes "Assign to new
   * baby" and routes into the Add-a-Baby flow (MOB-598).
   */
  private fun showBabyAssignedToast(
    reading: String,
    entry: List<ScaleEntry>,
    baby: BabyProfile,
    savedIds: List<Long>,
    sourceSku: String? = null,
    noOtherBaby: Boolean = false,
  ) {
    dialogQueueService.showToast(
      Toast.Custom(
        ReadingToast(
          reading = reading,
          type = ProductType.BABY,
          timestamp = "Just now",
          assignedTo = baby.name,
          assignToNewBaby = noOtherBaby,
          primaryAction = {
            dialogQueueService.dismissToast()
            if (noOtherBaby) {
              scope.launch { navigationService.navigateTo(AppRoute.AccountSettings.AddBaby()) }
            } else {
              showAssignMeasurementDialog(
                reading = reading,
                entry = entry,
                preSelectedBabyId = baby.id,
                previousEntryIds = savedIds,
                sourceSku = sourceSku,
              )
            }
          },
        ),
      ),
    )
  }

  /**
   * Builds a [BabyEntry] from an incoming scale reading. The reading weight is stored
   * in tenths-of-lb; the baby graph reads decigrams, so convert lb → decigrams to keep
   * storage and display consistent. [entryType] is `weight` and [source] is the originating
   * scale SKU (e.g. "0220"), so [BabyEntry.toUnifiedRequest] POSTs it to /v3/entries/ as a
   * baby-weight reading. Marked CREATE/unsynced; [IEntryService.addBabyEntry] persists and
   * syncs it (§2.16).
   */
  private fun ScaleEntry.toBabyEntry(
    babyId: String,
    accountId: String,
    sourceSku: String?,
  ): BabyEntry {
    val lbs = ConversionTools.convertStoredToLbs(scale.scaleEntry.weight)
    return BabyEntry(
      entry =
        entry.copy(
          id = 0L,
          accountId = accountId,
          // The baby scale's RTC is unreliable (reports ~1974/1980), which otherwise plotted the
          // reading decades in the past on the graph. A live weigh happens now, so stamp it with
          // the device time — same approach as the BPM live reading (MOB-598).
          entryTimestamp = DateTimeConverter.timestampToIso(System.currentTimeMillis()),
          operationType = OperationType.CREATE.name,
          isSynced = false,
        ),
      babyEntry =
        BabyEntryEntity(
          id = 0L,
          babyId = babyId,
          babyWeightDecigrams = ConversionTools.convertLbToDecigrams(lbs),
          entryNote = scale.scaleEntry.note,
          entryType = BabyEntryType.WEIGHT.value,
          source = sourceSku,
        ),
    )
  }

  private fun formatReadingForDisplay(
    entry: ScaleEntry,
    type: ProductType,
  ): String {
    val weight = entry.scale.scaleEntry.weight
    val unit = entry.entry.unit
    return when (type) {
      ProductType.BABY -> {
        // Normalise the native deci-pound reading to the canonical decigrams, then let the
        // shared SKU-aware converter do the lb/oz split — no bespoke oz math, no unit.label
        // (which is "lbs"/"lbs & oz" and wrong here). Always "<lb> lb <oz> oz".
        val decigrams = ConversionTools.convertLbToDecigrams(weight / 10.0)
        val (lbs, oz) = ConversionTools.convertBabyWeightToLbOz(decigrams, entry.scale.scaleEntry.source)
        "$lbs lb ${formatWeightValue(oz)} oz"
      }
      ProductType.BLOOD_PRESSURE -> {
        // Weight field stores systolic for BPM protocol entries
        "${formatWeightValue(weight)} ${unit.label}"
      }
      ProductType.MY_WEIGHT -> "${formatWeightValue(weight)} ${unit.label}"
    }
  }
}
