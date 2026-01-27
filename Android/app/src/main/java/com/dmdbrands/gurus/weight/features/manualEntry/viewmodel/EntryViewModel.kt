package com.dmdbrands.gurus.weight.features.manualEntry.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IAppSyncService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.features.common.helper.form.MultiFormGroup
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.common.strings.AppPopupStrings
import com.dmdbrands.gurus.weight.features.dashboard.string.DashboardString
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.toScaleEntry
import com.dmdbrands.gurus.weight.features.manualEntry.strings.EntryScreenStrings
import dagger.hilt.android.lifecycle.HiltViewModel
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
) : BaseIntentViewModel<EntryState, EntryIntent>(
  reducer = EntryReducer(),
) {
  private val TAG = "EntryViewModel"
  override fun provideInitialState(): EntryState =
    EntryState(
      form =
        MultiFormGroup.create(
          forms = EntryForm.create(),
        ),
    )

  init {
    viewModelScope.launch {
      // Step 1: Check for AppSync data
      val hasAppSyncData = appSyncService.appSyncDataForEditing.first() != null

      // Step 2: Create initial form only if no AppSync data
      if (!hasAppSyncData) {
        val entryForm = EntryForm.create(
          includeR4ScaleMetrics = true,
          weightUnit = accountService.activeAccountFlow.first()?.weightUnit,
          height = accountService.activeAccountFlow.first()?.height,
          isValueChangeAllowed = { _, _ ->
            !_state.value.form.forms.generalMetrics.controls.bodyMassIndex.touched
          },
        )
        handleIntent(
          EntryIntent.UpdateForm(
            form = MultiFormGroup.create(forms = entryForm),
          ),
        )
      } else {
        handleIntent(EntryIntent.UpdateMetricFieldsExpandedStatus(true))
      }

      // Step 3: Set up continuous flows
      launch {
        accountService.activeAccountFlow.map { it?.weightUnit }.distinctUntilChanged().collect {
          if (it != null) {
            handleIntent(EntryIntent.UpdateWeightUnit(it))
          }
        }
      }

      launch {
        deviceService.hasBluetoothWifiScale.collectLatest { hasBluetoothWifiScale ->
          val dashboardType = if (hasBluetoothWifiScale) {
            DashboardType.DASHBOARD_12_METRICS
          } else {
            DashboardType.DASHBOARD_4_METRICS
          }
          handleIntent(EntryIntent.UpdateDashboardType(dashboardType))
        }
      }
    }

    // Keep AppSync data collection separate
    viewModelScope.launch {
      appSyncService.appSyncDataForEditing.collectLatest { scaleEntry ->
        if (scaleEntry != null) {
          loadAppSyncData(scaleEntry)
        }
      }
    }
  }

  override fun handleIntent(intent: EntryIntent) {
    super.handleIntent(intent)
    when (intent) {
      is EntryIntent.Save -> {
        saveEntry()
      }

      is EntryIntent.UpdateOnRelaunch -> {
        viewModelScope.launch {
          val hasAppSyncData = appSyncService.appSyncDataForEditing.first() != null

          if (!hasAppSyncData) {
            val entryForm = EntryForm.create(
              includeR4ScaleMetrics = true,
              weightUnit = state.value.weightMode,
              height = accountService.activeAccountFlow.first()?.height,
              isValueChangeAllowed = { _, _ ->
                !_state.value.form.forms.generalMetrics.controls.bodyMassIndex.touched
              },
            )
            handleIntent(
              EntryIntent.UpdateForm(
                form = MultiFormGroup.create(forms = entryForm),
              ),
            )
          }
          // If AppSync data exists, leave the form alone - it's already properly set up
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
        if (state.value.form.isDirty || state.value.form.isTouched) {
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
        if (state.value.form.isDirty || state.value.form.isTouched) {
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

  private fun saveEntry() {
    dialogQueueService.showLoader(
      message = DashboardString.Loader.save,
    )
    viewModelScope.launch {
      val scaleEntry =
        _state.value.form.forms
          .toScaleEntry(_state.value.weightMode)
      try {
        entryService.addEntry(entry = scaleEntry)

        // Clear AppSync data after successful save
        appSyncService.setAppSyncDataForEditing(null)

        dialogQueueService.showToast(
          Toast(
            message = EntryScreenStrings.EntryAdded,
          ),
        )
        deactivate()
        navigationService.navigateBack(AppRoute.Home)
      } catch (e: Exception) {
        AppLog.e(TAG, "Error saving entry: ${e.message}", e)
        dialogQueueService.showToast(
          Toast(
            message = "Failed to save entry: ${e.message}",
          ),
        )
      } finally {
        dialogQueueService.dismissLoader()
      }
    }
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

        // Dispatch intent with height parameter
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
          Toast(message = "Failed to load AppSync data: ${e.message}"),
        )
      }
    }
  }
}
