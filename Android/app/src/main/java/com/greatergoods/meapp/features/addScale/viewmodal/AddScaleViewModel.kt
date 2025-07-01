package com.greatergoods.meapp.features.addScale.viewmodal

import com.greatergoods.meapp.domain.interfaces.IDialogUtility
import com.greatergoods.meapp.features.addScale.reducer.AddScaleFormControls
import com.greatergoods.meapp.features.addScale.reducer.AddScaleIntent
import com.greatergoods.meapp.features.addScale.reducer.AddScaleReducer
import com.greatergoods.meapp.features.addScale.reducer.AddScaleState
import com.greatergoods.meapp.features.common.helper.form.FormGroup
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AddScaleViewModel @Inject constructor(
    private val dialogUtility: IDialogUtility,
) : BaseIntentViewModel<AddScaleState, AddScaleIntent>(AddScaleReducer()) {

    override fun provideInitialState(): AddScaleState {
        return AddScaleState(
            form = FormGroup(AddScaleFormControls.create()),
        )
    }

    override fun handleIntent(intent: AddScaleIntent) {
        super.handleIntent(intent)
        when (intent) {
            is AddScaleIntent.Submit -> {
            }

            is AddScaleIntent.ShowHelp -> {
                showModelNumberHelpPopup()
            }

            else -> {}
        }
    }

    /**
     * Shows the Model number help popup.
     */
    private fun showModelNumberHelpPopup() {
        dialogUtility.showModelNumberHelpDialog()
    }
}
