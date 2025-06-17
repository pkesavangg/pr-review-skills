package com.greatergoods.meapp.features.entry.viewmodel

import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.features.common.components.DateTimeValue
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.common.helper.form.FormGroup
import com.greatergoods.meapp.features.common.helper.form.FormValidations
import kotlinx.coroutines.CoroutineScope

data class EntryFormControls(
    val weight: FormControl<String>,
    val dateTime: FormControl<DateTimeValue>,
) {
    companion object {
        fun create(scope: CoroutineScope): EntryFormControls = EntryFormControls(
            weight = FormControl(
                initialValue = "",
                validators = listOf(FormValidations.required()),
                asyncValidators = emptyList(),
                scope = scope,
            ),
            dateTime = FormControl(
                initialValue = DateTimeValue.DateTime(System.currentTimeMillis(), 12, 0),
                validators = emptyList(),
                asyncValidators = emptyList(),
                scope = scope,
            ),
        )
    }
}

data class EntryState(
    val form: FormGroup<EntryFormControls>,
    val isLoading: Boolean = false,
) : IReducer.State

/**
 * Intent for entry actions, such as loading, selecting, adding, and deleting entries.
 */
sealed interface EntryIntent : IReducer.Intent

/**
 * Reducer for the entry state, handling intents to update months, entries, and errors.
 */
class EntryReducer : IReducer<EntryState, EntryIntent> {
    override fun reduce(state: EntryState, intent: EntryIntent): EntryState? = state
}
