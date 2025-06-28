package com.greatergoods.meapp.features.manualEntry.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.services.IEntryService
import com.greatergoods.meapp.features.common.helper.form.MultiFormGroup
import com.greatergoods.meapp.features.common.model.Toast
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import com.greatergoods.meapp.features.manualEntry.helper.EntryHelper.toScaleEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

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
) : BaseIntentViewModel<EntryState, EntryIntent>(
    reducer = EntryReducer(),
) {
    override fun provideInitialState(): EntryState =
        EntryState(
            form = MultiFormGroup.create(
                forms = EntryForm.create(true),
            ),
        )

    override fun handleIntent(intent: EntryIntent) {
        super.handleIntent(intent)
        when (intent) {
            is EntryIntent.Save -> {
                saveEntry()
            }
        }
    }

    private fun saveEntry() {
        if (!state.value.form.isValid) {
            return
        }
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
