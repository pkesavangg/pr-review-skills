package com.greatergoods.meapp.features.entry.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.domain.services.IEntryService
import com.greatergoods.meapp.features.common.helper.form.FormGroup
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel for the entry feature, managing state and handling entry intents.
 *
 * @property entryService The entry service for fetching and updating entries.
 */
@HiltViewModel
class EntryViewModel @Inject constructor(
    private val entryService: IEntryService
) : BaseIntentViewModel<EntryState, EntryIntent>(
    reducer = EntryReducer(),
) {
    override fun provideInitialState(): EntryState {
        return EntryState(
            form = FormGroup(
                EntryFormControls.create(viewModelScope, true),
            ),
        )
    }
}





