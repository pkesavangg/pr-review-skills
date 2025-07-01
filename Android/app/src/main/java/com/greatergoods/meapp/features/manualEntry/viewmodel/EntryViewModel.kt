package com.greatergoods.meapp.features.manualEntry.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.services.IAccountService
import com.greatergoods.meapp.domain.services.IEntryService
import com.greatergoods.meapp.features.common.helper.form.MultiFormGroup
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.common.model.Toast
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import com.greatergoods.meapp.features.common.strings.AppPopupStrings
import com.greatergoods.meapp.features.manualEntry.helper.EntryHelper.toScaleEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
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
) : BaseIntentViewModel<EntryState, EntryIntent>(
    reducer = EntryReducer(),
) {
    override fun provideInitialState(): EntryState =
        EntryState(
            form = MultiFormGroup.create(
                forms = EntryForm.create(),
            ),
        )

    init {
        viewModelScope.launch {
            val entryForm = EntryForm.create(
                includeR4ScaleMetrics = true,
                weightUnit = accountService.activeAccountFlow.first()?.weightUnit,
                height = accountService.activeAccountFlow.first()?.height,
            )
            handleIntent(
                EntryIntent.UpdateForm(
                    form = MultiFormGroup.create(
                        forms = entryForm,
                    ),
                ),
            )
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
                    .toScaleEntry(_state.value.weightMode.value)
            try {
                entryService.syncOperations(newEntries = listOf(scaleEntry))
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
}
