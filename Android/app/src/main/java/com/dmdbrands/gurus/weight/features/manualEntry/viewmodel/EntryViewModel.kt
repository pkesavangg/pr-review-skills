package com.dmdbrands.gurus.weight.features.manualEntry.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IAppSyncService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.features.common.helper.form.MultiFormGroup
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.common.strings.AppPopupStrings
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.toScaleEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume
import android.util.Log

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
) : BaseIntentViewModel<EntryState, EntryIntent>(
    reducer = EntryReducer(),
) {
    override fun provideInitialState(): EntryState =
        EntryState(
            form =
                MultiFormGroup.create(
                    forms = EntryForm.create(),
                ),
        )

    init {
        viewModelScope.launch {
            val entryForm =
                EntryForm.create(
                    includeR4ScaleMetrics = true,
                    weightUnit = _state.value.weightMode,
                    height = accountService.activeAccountFlow.first()?.height,
                )
            handleIntent(
                EntryIntent.UpdateForm(
                    form =
                        MultiFormGroup.create(
                            forms = entryForm,
                        ),
                ),
            )
        }
        viewModelScope.launch {
            accountService.activeAccountFlow.map { it?.weightUnit }.distinctUntilChanged().collect {
                handleIntent(
                    EntryIntent.UpdateWeightUnit(it ?: WeightUnit.LB),
                )
            }
        }

        // Check if there's AppSync data to load
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
                            message = AppPopupStrings.UnsavedChanges.Message,
                            onConfirm = {
                                    if (!isResumed) {
                                        isResumed = true
                                        onConfirm()
                                        deactivate()
                                        _state.value.form.resetForm()
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
            navigationService.unregisterOnDeactivate(AppRoute.Main.Entry)
        }
    }

    private fun saveEntry() {
        dialogQueueService.showLoader(
            message = "saving entry...",
        )
        viewModelScope.launch {
            val scaleEntry =
                _state.value.form.forms
                    .toScaleEntry(_state.value.weightMode)
            try {
                entryService.addEntry(entry = scaleEntry)
                dialogQueueService.showToast(
                    Toast(
                        message = "entry saved successfully!",
                    ),
                )
                _state.value.form.resetForm()
                navigationService.navigateBack(AppRoute.Home)
            } catch (e: Exception) {
                AppLog.e("EntryViewModel", "Error saving entry: ${e.message}", e)
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
    private fun loadAppSyncData(scaleEntry: com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry, height: Int? = null) {
        viewModelScope.launch {
            try {
                val currentAccount = accountService.activeAccountFlow.first()
                val finalHeight = height ?: currentAccount?.height

                // Dispatch intent with height parameter, following Profile pattern exactly
                handleIntent(
                    EntryIntent.LoadAppSyncData(
                        scaleEntry = scaleEntry,
                        height = finalHeight
                    )
                )
                AppLog.i("EntryViewModel", "AppSync data loading intent dispatched with height: $finalHeight")
            } catch (e: Exception) {
                AppLog.e("EntryViewModel", "Failed to load AppSync data", e.toString())
                dialogQueueService.showToast(
                    Toast(message = "Failed to load AppSync data: ${e.message}")
                )
            }
        }
    }
}
