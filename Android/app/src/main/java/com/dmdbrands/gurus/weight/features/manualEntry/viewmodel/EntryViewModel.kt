package com.dmdbrands.gurus.weight.features.manualEntry.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IAnalyticsService
import com.dmdbrands.gurus.weight.domain.services.IAppSyncService
import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BabyEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BpmEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity
import com.dmdbrands.gurus.weight.domain.enums.BabyEntryType
import com.dmdbrands.gurus.weight.domain.model.api.entry.EntrySource
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BabyEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BpmEntry
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.features.common.helper.AccountHelper.isMetricUnit
import com.dmdbrands.gurus.weight.features.common.helper.form.MultiFormGroup
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.ReadingToast
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.common.strings.AppPopupStrings
import com.dmdbrands.gurus.weight.features.dashboard.string.DashboardString
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.toScaleEntry
import com.dmdbrands.gurus.weight.features.manualEntry.strings.EntryScreenStrings
import com.dmdbrands.gurus.weight.core.di.ApplicationScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import java.util.Locale
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * ViewModel for the entry feature, managing state and handling entry intents.
 *
 * @property entryService The entry service for fetching and updating entries.
 */
@HiltViewModel
class EntryViewModel
@Inject
constructor(
  private val entryService: IEntryService,
  private val accountService: IAccountService,
  private val appSyncService: IAppSyncService,
  private val deviceService: IDeviceService,
  private val analyticsService: IAnalyticsService,
  private val userDataStore: UserDataStore,
  // App-lifetime scope: the saved-to-log toast's VIEW is tapped AFTER this screen pops (so
  // viewModelScope is already cancelled). Navigating from the app scope keeps it working.
  @ApplicationScope private val appScope: CoroutineScope,
) : BaseIntentViewModel<EntryState, EntryIntent>(
  reducer = EntryReducer(),
) {
  private val TAG = "EntryViewModel"

  // Short settle delay between a successful save and showing the success toast
  // so the dashboard reflects the new entry before navigating back (MOB-183).
  private val ENTRY_SAVE_SETTLE_DELAY_MS = 1_000L

  override fun provideInitialState(): EntryState = EntryState()

  init {
    // Set weight unit synchronously from current account to avoid flash of default LB
    accountService.activeAccount.value?.weightUnit?.let {
      handleIntent(EntryIntent.UpdateWeightUnit(it))
    }

    // Set up continuous flows
    viewModelScope.launch {
      accountService.activeAccountFlow.map { it?.weightUnit }.distinctUntilChanged().collect {
        if (it != null) {
          handleIntent(EntryIntent.UpdateWeightUnit(it))
        }
      }
    }

    // Baby entry follows the My Kids unit shown in Settings — the device-local
    // babyWeightUnit (UNSPECIFIED → LB_OZ), NOT account.measurementUnits. On a fresh signup
    // weightUnit is only ever kg/lb, so measurementUnits is metric/lb-decimal and would never
    // yield the lb-oz baby default; this flow is the same source Settings reads. (MOB-1223)
    viewModelScope.launch {
      userDataStore.babyWeightUnitForCurrentAccountFlow.distinctUntilChanged().collect {
        handleIntent(EntryIntent.UpdateBabyUnit(it))
      }
    }

    viewModelScope.launch {
      deviceService.hasBluetoothWifiScale.collectLatest { hasBluetoothWifiScale ->
        val dashboardType = if (hasBluetoothWifiScale) {
          DashboardType.DASHBOARD_12_METRICS
        } else {
          DashboardType.DASHBOARD_4_METRICS
        }
        handleIntent(EntryIntent.UpdateDashboardType(dashboardType))
      }
    }

    // AppSync data collection
    viewModelScope.launch {
      appSyncService.appSyncDataForEditing.collectLatest { scaleEntry ->
        if (scaleEntry != null) {
          loadAppSyncData(scaleEntry)
        }
      }
    }
  }

  /**
   * Start observing product selection changes. Called from EntryScreen's
   * LaunchedEffect so productSelectionManager (field-injected by Hilt
   * via BaseViewModel) is guaranteed to be initialized.
   */
  fun observeProductSelection() {
    viewModelScope.launch {
      productSelectionManager.selectedProduct.collectLatest { product ->
        initProductForm(product)
      }
    }
  }

  private suspend fun initProductForm(product: ProductSelection) {
    when (product) {
      is ProductSelection.MyWeight -> {
        val hasAppSyncData = appSyncService.appSyncDataForEditing.first() != null
        if (!hasAppSyncData) {
          val activeAccount = accountService.activeAccountFlow.first()
          val entryForm = EntryForm.create(
            includeR4ScaleMetrics = true,
            weightUnit = activeAccount?.weightUnit,
            height = activeAccount?.height,
            isValueChangeAllowed = { _, _ ->
              !_state.value.form.forms.generalMetrics.controls.bodyMassIndex.touched
            },
          )
          handleIntent(
            EntryIntent.UpdateForm(form = MultiFormGroup.create(forms = entryForm)),
          )
        } else {
          handleIntent(EntryIntent.UpdateMetricFieldsExpandedStatus(true))
        }
      }
      is ProductSelection.BloodPressure -> {
        handleIntent(
          EntryIntent.UpdateActiveForm(
            ActiveEntryForm.BloodPressure(
              form = MultiFormGroup.create(forms = BloodPressureEntryForm.create()),
            ),
          ),
        )
      }
      is ProductSelection.Baby -> {
        handleIntent(
          EntryIntent.UpdateActiveForm(
            ActiveEntryForm.Baby(
              form = MultiFormGroup.create(forms = BabyEntryForm.create(_state.value.babyWeightMode)),
              // Capture the baby this form is being built for, so the save uses it instead of the
              // global selection at save time (MOB-1449).
              profile = product.profile,
            ),
          ),
        )
      }
      // No profile yet (MOB-416): Manual Entry shows an empty state, no form to build.
      is ProductSelection.BabyScale -> Unit
    }
  }

  override fun handleIntent(intent: EntryIntent) {
    super.handleIntent(intent)
    when (intent) {
      is EntryIntent.Save -> {
        when (_state.value.activeForm) {
          is ActiveEntryForm.Weight -> saveEntry()
          is ActiveEntryForm.BloodPressure -> saveBloodPressureEntry()
          is ActiveEntryForm.Baby -> saveBabyEntry()
        }
      }

      is EntryIntent.UpdateOnRelaunch -> {
        val account = accountService.activeAccount.value
        val weightUnit = account?.weightUnit ?: state.value.weightMode
        handleIntent(EntryIntent.UpdateWeightUnit(weightUnit))
        viewModelScope.launch {
          // Only (re)build the weight form for the weight product. For BP/Baby the active
          // form is owned by observeProductSelection(); rebuilding a weight form here would
          // clobber it (activeForm → Weight) and hide the BP/Baby entry form. (MOB-592)
          if (productSelectionManager.selectedProduct.value !is ProductSelection.MyWeight) {
            return@launch
          }
          val hasAppSyncData = appSyncService.appSyncDataForEditing.first() != null
          if (!hasAppSyncData) {
            val activeAccount = accountService.activeAccountFlow.first()
            val entryForm = EntryForm.create(
              includeR4ScaleMetrics = true,
              weightUnit = activeAccount?.weightUnit ?: state.value.weightMode,
              height = activeAccount?.height,
              isValueChangeAllowed = { _, _ ->
                !_state.value.form.forms.generalMetrics.controls.bodyMassIndex.touched
              },
            )
            handleIntent(EntryIntent.UpdateForm(form = MultiFormGroup.create(forms = entryForm)))
          }
        }
      }

      is EntryIntent.UpdateBabyUnit -> {
        // The baby form's validators are frozen at build time from babyWeightMode. Because the
        // baby unit loads asynchronously (no synchronous seed like the adult weightUnit), the
        // active baby form may have been built with the default LB_OZ whole-number validators
        // before the real unit arrived — leaving a kg/lb-decimal single-field layout wired to
        // lb-oz validators that reject valid decimals. Rebuild the active baby form so its
        // validators match the unit now being rendered. (MOB-1223)
        val activeForm = _state.value.activeForm
        if (activeForm is ActiveEntryForm.Baby) {
          handleIntent(
            EntryIntent.UpdateActiveForm(
              ActiveEntryForm.Baby(
                form = MultiFormGroup.create(forms = BabyEntryForm.create(intent.weightUnit)),
                profile = activeForm.profile,
              ),
            ),
          )
        }
      }

      is EntryIntent.EarlyExit -> {
        earlyExitToHome()
      }

      else -> null
    }
  }

  fun initDeactivate(onConfirm: () -> Unit) {
    viewModelScope.launch {
      navigationService.registerOnDeactivate(AppRoute.Main.Entry) {
        if (state.value.activeForm.isDirty) {
          return@registerOnDeactivate suspendCancellableCoroutine { cont ->
            var isResumed = false

            dialogQueueService.enqueue(
              DialogModel.Confirm(
                title = AppPopupStrings.UnsavedChanges.ManualEntryTitle,
                message = AppPopupStrings.UnsavedChanges.ManualEntryMessage,
                confirmText = AppPopupStrings.UnsavedChanges.Exit,
                cancelText = AppPopupStrings.UnsavedChanges.Return,
                onConfirm = {
                  if (!isResumed) {
                    isResumed = true
                    onConfirm()
                    deactivate()
                    cont.resume(true)
                  }
                },
              onCancel = {
                  if (!isResumed) {
                    isResumed = true
                    cont.resume(false)
                  }
                },
              ),
            )
          }
        } else {
          return@registerOnDeactivate true
        }
      }
    }
  }

  fun Exit() {
    viewModelScope.launch {
      navigationService.registerOnDeactivate(AppRoute.Main.Entry) {
        if (state.value.activeForm.isDirty) {
          return@registerOnDeactivate suspendCancellableCoroutine { cont ->
            var isResumed = false

            dialogQueueService.enqueue(
              DialogModel.Confirm(
                title = AppPopupStrings.UnsavedChanges.ManualEntryTitle,
                message = AppPopupStrings.UnsavedChanges.ManualEntryMessage,
                confirmText = AppPopupStrings.UnsavedChanges.Exit,
                cancelText = AppPopupStrings.UnsavedChanges.Return,
                onConfirm = {
                  if (!isResumed) {
                    isResumed = true
                    deactivate()
                    cont.resume(true)
                  }
                },
                onCancel = {
                  if (!isResumed) {
                    isResumed = true
                    cont.resume(false)
                  }
                },
              ),
            )
          }
        } else {
          return@registerOnDeactivate true
        }
      }
    }
  }

  fun deactivate() {
    viewModelScope.launch {
      handleIntent(EntryIntent.UpdateMetricFieldsExpandedStatus(false))
      navigationService.unregisterOnDeactivate(AppRoute.Main.Entry)
      // Clear AppSync data when exiting EntryScreen
      appSyncService.setAppSyncDataForEditing(null)
    }
  }

  /**
   * Handles early exit with unsaved changes confirmation dialog.
   * Similar to initDeactivate but for manual back button handling.
   */
  fun earlyExit() {
    Exit()
  }

  /**
   * Convenience method for early exit that navigates back to home.
   * Use this for simple back button handling.
   */
  fun earlyExitToHome() {
    earlyExit()
  }

  /**
   * Manual-entry confirmation card (Figma 30456-24170): "New Reading saved to your log" with the
   * reading and a single VIEW action that opens this entry's History detail — replaces the plain
   * "Entry added" toast.
   */
  /**
   * Baby manual-entry confirmation: show the rich "saved to your log" card (with VIEW) like the
   * weight/BP flow when a weight was entered; fall back to the plain toast for a length-only entry.
   */
  private fun showBabySavedToast(babyEntry: BabyEntry?, babyName: String?) {
    val weightDecigrams = babyEntry?.babyWeightDecigrams
    if (babyEntry != null && weightDecigrams != null) {
      // Format with the account's real unit preference so the card matches History/dashboard
      // (kg for metric users) instead of always showing lb/oz. (MOB-598)
      val isMetric = accountService.activeAccount.value?.isMetricUnit() ?: false
      showSavedToLogToast(
        reading = ConversionTools.convertBabyWeightToDisplay(weightDecigrams, source = null, isMetric = isMetric),
        type = ProductType.BABY,
        entryTimestamp = babyEntry.entry.entryTimestamp,
        babyName = babyName,
      )
    } else {
      dialogQueueService.showToast(
        Toast.Simple(
          title = EntryScreenStrings.EntryAddedTitle,
          message = EntryScreenStrings.EntryAdded,
        ),
      )
    }
  }

  private fun showSavedToLogToast(reading: String, type: ProductType, entryTimestamp: String, babyName: String? = null) {
    // VIEW opens this entry's History detail, whose query matches the bucketed key the History list
    // passes (a "Mon YYYY" month label for weight/BP, a "yyyy-MM-dd" day key for baby) — not the raw
    // entryTimestamp, which lands on an empty detail. See EntryHelper.historyDetailKey.
    val detailKey = EntryHelper.historyDetailKey(entryTimestamp, type)
    dialogQueueService.showToast(
      Toast.Custom(
        ReadingToast(
          reading = reading,
          type = type,
          timestamp = "Just now",
          savedToLog = true,
          // Baby manual entry names the selected baby in the card title ("New Reading Received for
          // NAME"), mirroring the device-sync single-baby card. Weight/BP stay unnamed.
          assignedTo = babyName,
          onView = {
            appScope.launch {
              navigationService.navigateTo(AppRoute.History.MonthDetails(detailKey, type))
            }
          },
        ),
      ),
    )
  }

  private fun saveEntry() {
    dialogQueueService.showLoader(
      message = DashboardString.Loader.save,
    )
    viewModelScope.launch {
      val accountId = accountService.activeAccountFlow.first()?.id ?: return@launch
      val scaleEntry =
        _state.value.form.forms
          .toScaleEntry(_state.value.weightMode, accountId)
      try {
        entryService.addEntry(entry = scaleEntry)
        analyticsService.logEvent(IAnalyticsService.Events.MANUAL_ENTRY_CREATED)

        // Clear AppSync data after successful save
        appSyncService.setAppSyncDataForEditing(null)

        // Brief settle delay so the just-saved entry is reflected on the dashboard before the
        // success card appears and we navigate back (MOB-183). addEntry() already awaits the
        // local save + server-sync attempt, so this only smooths the hand-off.
        //
        // TODO(MOB-183 follow-up): this fixed delay is a short-term smoothing, not a real fix — it
        // will still lag on a slow device and over-hold the loader on a fast one. The systematic
        // fix is to have the dashboard observe its data reactively (await the new entry appearing
        // in the source flow / await an explicit refresh) and gate navigateBack on that signal
        // instead of a guessed sleep. Same anti-pattern as DashboardViewModel's delay(300) calls —
        // worth tracking together in a dedicated follow-up ticket.
        delay(ENTRY_SAVE_SETTLE_DELAY_MS)

        val isMetric = _state.value.weightMode == WeightUnit.KG
        val displayValue =
          ConversionTools.convertStoredToDisplay(scaleEntry.scale.scaleEntry.weight, isMetric)
        showSavedToLogToast(
          reading = "${String.format(Locale.US, "%.1f", displayValue)} ${_state.value.weightMode.label}",
          type = ProductType.MY_WEIGHT,
          entryTimestamp = scaleEntry.entry.entryTimestamp,
        )
        deactivate()
        navigationService.navigateBack(AppRoute.Home)
      } catch (e: Exception) {
        AppLog.e(TAG, "Error saving entry: ${e.message}", e)
        dialogQueueService.showToast(
          Toast.Simple(
            title = EntryScreenStrings.EntryErrorTitle,
            message = EntryScreenStrings.EntryErrorMessage,
          ),
        )
      } finally {
        dialogQueueService.dismissLoader()
      }
    }
  }

  private fun saveBloodPressureEntry() {
    val bpForm = (_state.value.activeForm as? ActiveEntryForm.BloodPressure)?.form ?: return
    dialogQueueService.showLoader(message = DashboardString.Loader.save)
    viewModelScope.launch {
      val accountId = accountService.activeAccountFlow.first()?.id ?: return@launch
      try {
        val controls = bpForm.forms.bloodPressure.controls
        val systolic = controls.systolic.value.toIntOrNull() ?: 0
        val diastolic = controls.diastolic.value.toIntOrNull() ?: 0
        val pulse = controls.pulse.value.toIntOrNull() ?: 0
        val meanArterial = ((systolic + 2 * diastolic) / 3).toString()
        val note = controls.notes.value.ifBlank { null }

        val bpmEntryEntity = BpmEntryEntity(
          id = 0L,
          systolic = systolic,
          diastolic = diastolic,
          pulse = pulse,
          meanArterial = meanArterial,
          note = note,
        )
        val entryEntity = EntryEntity(
          accountId = accountId,
          // Persist the user's chosen date/time from the form, not "now" — otherwise a back-dated
          // reading was silently stamped with the current time and mis-grouped in History (MOB-1427).
          // Matches the weight (EntryHelper.toScaleEntry) and baby (buildBabyEntry) save paths.
          entryTimestamp = DateTimeConverter.timestampToIso(controls.dateTime.value.getTimestamp()),
          operationType = "create",
          deviceType = "manual",
          deviceId = "",
        )
        val bpmEntry = BpmEntry(
          entry = entryEntity,
          bpmEntry = bpmEntryEntity,
        )
        entryService.addEntry(entry = bpmEntry)
        analyticsService.logEvent(IAnalyticsService.Events.MANUAL_ENTRY_CREATED)
        showSavedToLogToast(
          reading = "$systolic/$diastolic",
          type = ProductType.BLOOD_PRESSURE,
          entryTimestamp = entryEntity.entryTimestamp,
        )
        // Reset form
        handleIntent(
          EntryIntent.UpdateActiveForm(
            ActiveEntryForm.BloodPressure(
              form = MultiFormGroup.create(forms = BloodPressureEntryForm.create()),
            ),
          ),
        )
        deactivate()
        navigationService.navigateBack(AppRoute.Home)
      } catch (e: Exception) {
        AppLog.e(TAG, "Error saving BP entry: ${e.message}", e)
        dialogQueueService.showToast(
          Toast.Simple(
            title = EntryScreenStrings.EntryErrorTitle,
            message = EntryScreenStrings.EntryErrorMessage,
          ),
        )
      } finally {
        dialogQueueService.dismissLoader()
      }
    }
  }

  private fun saveBabyEntry() {
    val activeBabyForm = _state.value.activeForm as? ActiveEntryForm.Baby ?: return
    val babyForm = activeBabyForm.form
    // Use the baby captured when the form was built, NOT the global selection at save time — the
    // selection can shift between opening and saving and would file the entry on the wrong baby
    // (MOB-1449).
    val babyProfile = activeBabyForm.profile
    val babyId = babyProfile.id

    // Match Smart Baby (babyApp): reject entries dated before the baby's birthdate. The
    // comparison is calendar-day only (time-of-day ignored), and surfaces a toast without
    // saving — same behaviour and copy as babyApp's add-weight/add-length screens. (MOB-592)
    val entryTimestamp = babyForm.forms.baby.controls.dateTime.value.getTimestamp()
    if (isBeforeBirthdate(entryTimestamp, babyProfile.birthdate)) {
      dialogQueueService.showToast(Toast.Simple(message = EntryScreenStrings.EntryBeforeBirthdate))
      return
    }

    dialogQueueService.showLoader(message = DashboardString.Loader.save)
    viewModelScope.launch {
      val accountId = accountService.activeAccountFlow.first()?.id
      if (accountId == null) {
        // Practically unreachable for a logged-in user, but surface it like the other save
        // paths rather than dismissing the loader with no feedback. (MOB-592)
        AppLog.w(TAG, "No active account; cannot save baby entry")
        dialogQueueService.showToast(
          Toast.Simple(
            title = EntryScreenStrings.EntryErrorTitle,
            message = EntryScreenStrings.EntryErrorMessage,
          ),
        )
        dialogQueueService.dismissLoader()
        return@launch
      }
      try {
        // addEntry persists locally (isSynced=false) and syncs to POST /v3/entries/
        // (category=baby) — the same path manual BP uses. One combined local row carries BOTH
        // measures: the local UNIQUE(accountId, entryTimestamp) index allows only one row per
        // timestamp, and the POST split into distinct §2.16 weight/length requests happens later
        // in the mapper. Null when no measure was entered.
        val babyEntry = buildBabyEntry(babyForm.forms.baby.controls, accountId, babyId, _state.value.babyWeightMode)
        babyEntry?.let { entryService.addEntry(it) }
        analyticsService.logEvent(IAnalyticsService.Events.MANUAL_ENTRY_CREATED)
        showBabySavedToast(babyEntry, babyProfile.name)
        handleIntent(
          EntryIntent.UpdateActiveForm(
            ActiveEntryForm.Baby(
              form = MultiFormGroup.create(forms = BabyEntryForm.create(_state.value.babyWeightMode)),
              profile = babyProfile,
            ),
          ),
        )
        // Match the weight/BP save flow: just deactivate + pop back to where the
        // entry screen was opened from. (Rebuilding the form here re-activated the
        // screen and prevented the back navigation — the form is recreated by
        // observeProductSelection the next time Entry is opened anyway.)
        deactivate()
        navigationService.navigateBack(AppRoute.Home)
      } catch (e: Exception) {
        AppLog.e(TAG, "Error saving baby entry: ${e.message}", e)
        dialogQueueService.showToast(
          Toast.Simple(
            title = EntryScreenStrings.EntryErrorTitle,
            message = EntryScreenStrings.EntryErrorMessage,
          ),
        )
      } finally {
        dialogQueueService.dismissLoader()
      }
    }
  }

  /**
   * True when [entryTimestamp] falls on a calendar day strictly before the baby's [birthdate]
   * (an ISO date like "2026-01-10"). Day-level comparison in the device's zone, mirroring
   * babyApp's getDateOnly check. Returns false when the birthdate is missing/unparseable so
   * we never block a save on bad profile data.
   */
  private fun isBeforeBirthdate(entryTimestamp: Long, birthdate: String?): Boolean {
    if (birthdate.isNullOrBlank()) return false
    val birthDay = runCatching { LocalDate.parse(birthdate.take(10)) }.getOrNull() ?: return false
    val entryDay = Instant.ofEpochMilli(entryTimestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    return entryDay.isBefore(birthDay)
  }

  /**
   * Reads the baby form and builds one [BabyEntry] per provided measure. Weight and length
   * are distinct entryTypes (§2.16), so an entry with both yields two entries.
   */
  /**
   * Builds the single combined baby [BabyEntry] for manual entry, carrying BOTH measures
   * (weight + length) on one local row. The local UNIQUE(accountId, entryTimestamp) index
   * allows only one row per timestamp; the POST split into distinct §2.16 weight/length
   * requests happens later in the mapper. Returns null when neither measure was entered.
   */
  private fun buildBabyEntry(
    controls: BabyEntryFormControls,
    accountId: String,
    babyId: String,
    weightUnit: WeightUnit,
  ): BabyEntry? {
    val timestamp = DateTimeConverter.timestampToIso(controls.dateTime.value.getTimestamp())
    val note = controls.notes.value.ifBlank { null }
    // The typed value's unit follows the account's Unit Type (MOB-1223); convert to the canonical
    // decigrams/mm the local row + POST both use (matches Smart Baby / babyApp storage contract).
    val weightDecigrams = when (weightUnit) {
      WeightUnit.LB_OZ -> {
        val lbs = controls.weight.value.toIntOrNull() ?: 0
        // oz uses the adult BODY_COMP input: value is the raw digit string with an implicit
        // 1-place decimal ("45" → 4.5 oz), so divide by 10 to recover the real ounces. (MOB-1223)
        val oz = controls.weightOz.value.toDoubleOrNull()?.div(10.0) ?: 0.0
        if (lbs > 0 || oz > 0) ConversionTools.convertLbOzToDecigrams(lbs, oz) else null
      }
      WeightUnit.KG -> controls.weight.value.toDoubleOrNull()
        ?.takeIf { it > 0 }?.let { ConversionTools.convertKgToDecigrams(it) }
      WeightUnit.LB -> controls.weight.value.toDoubleOrNull()
        ?.takeIf { it > 0 }?.let { ConversionTools.convertLbToDecigrams(it) }
    }
    // length is BODY_COMP raw digits with an implicit 1-place decimal ("205" → 20.5), so divide
    // by 10 to recover the real value — same convention as ounces. (MOB-1223)
    val lengthValue = controls.length.value.toDoubleOrNull()?.div(10.0)?.takeIf { it > 0 }
    val lengthMm = lengthValue?.let {
      if (weightUnit == WeightUnit.KG) ConversionTools.convertCmToMm(it) else ConversionTools.convertInchesToMm(it)
    }
    if (weightDecigrams == null && lengthMm == null) return null
    return BabyEntry(
      entry = EntryEntity(
        accountId = accountId,
        entryTimestamp = timestamp,
        operationType = "create",
        deviceType = "manual",
        deviceId = "",
      ),
      babyEntry = BabyEntryEntity(
        id = 0L,
        babyId = babyId,
        babyWeightDecigrams = weightDecigrams,
        babyLengthMillimeters = lengthMm,
        entryNote = note,
        // Primary type for the local row; POST splits per present measure regardless.
        entryType = if (weightDecigrams != null) BabyEntryType.WEIGHT.value else BabyEntryType.MEASURE_LENGTH.value,
        source = EntrySource.MANUAL.value,
      ),
    )
  }

  /**
   * Loads AppSync data into the form for editing, following ProfileViewModel pattern.
   */
  private fun loadAppSyncData(
    scaleEntry: com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry,
    height: Int? = null
  ) {
    viewModelScope.launch {
      try {
        val currentAccount = accountService.activeAccountFlow.first()
        val finalHeight = height ?: currentAccount?.height

        // Expand metrics and dispatch intent with height parameter
        handleIntent(EntryIntent.UpdateMetricFieldsExpandedStatus(true))
        handleIntent(
          EntryIntent.LoadAppSyncData(
            scaleEntry = scaleEntry,
            height = finalHeight,
          ),
        )

        // Wait for the form to be updated, then mark as touched/dirty for AppSync editing
        _state.value.form.markAllAsTouched()
        _state.value.form.markAllAsDirty()

        // Specifically mark the weight control as touched and dirty
        _state.value.form.forms.weightDateTime.controls.weight.markAsTouched()
        _state.value.form.forms.weightDateTime.controls.weight.markAsDirty()

        // Validate the form
        _state.value.form.validate()

        AppLog.i(
          TAG,
          "AppSync data loaded and form marked as touched/dirty - isDirty: ${_state.value.form.isDirty}, isTouched: ${_state.value.form.isTouched}",
        )
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to load AppSync data", e)
        dialogQueueService.showToast(
          Toast.Simple(message = "Failed to load AppSync data: ${e.message}"),
        )
      }
    }
  }
}
