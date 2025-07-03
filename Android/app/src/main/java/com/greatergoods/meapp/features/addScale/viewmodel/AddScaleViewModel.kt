package com.greatergoods.meapp.features.addScale.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.domain.interfaces.IDialogUtility
import com.greatergoods.meapp.domain.repository.IDeviceService
import com.greatergoods.meapp.features.addScale.reducer.AddScaleFormControls
import com.greatergoods.meapp.features.addScale.reducer.AddScaleIntent
import com.greatergoods.meapp.features.addScale.reducer.AddScaleReducer
import com.greatergoods.meapp.features.addScale.reducer.AddScaleState
import com.greatergoods.meapp.features.common.enums.ScaleSetupType
import com.greatergoods.meapp.features.common.helper.form.FormGroup
import com.greatergoods.meapp.features.common.model.ScaleInfo
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddScaleViewModel @Inject constructor(
    private val dialogUtility: IDialogUtility,
    private val deviceService: IDeviceService,
) : BaseIntentViewModel<AddScaleState, AddScaleIntent>(AddScaleReducer()) {

    override fun provideInitialState(): AddScaleState {
        return AddScaleState(
            form = FormGroup(AddScaleFormControls.Companion.create()),
        )
    }

    override fun handleIntent(intent: AddScaleIntent) {
        super.handleIntent(intent)
        when (intent) {
            is AddScaleIntent.Submit -> {
                // TODO: Handle scale submission
            }

            is AddScaleIntent.ShowHelp -> {
                showModelNumberHelpPopup()
            }

            is AddScaleIntent.OpenScaleChooser -> {
                navigateTo(AppRoute.AccountSettings.ChooseScale)
            }

            is AddScaleIntent.OpenScaleSettings -> {
                navigateTo(AppRoute.AccountSettings.ScaleSettings(intent.broadcastId))
            }

            else -> {}
        }
    }

    init {
        viewModelScope.launch {
            // Collect saved scales from DeviceService
            deviceService.savedScales.collectLatest { devices ->
                handleIntent(AddScaleIntent.SetSavedScales(devices))
            }
        }
    }

    /**
     * Shows the Model number help popup.
     */
    private fun showModelNumberHelpPopup() {
        dialogUtility.showModelNumberHelpDialog()
    }

    private fun navigateTo(route: AppRoute) {
        viewModelScope.launch {
            navigationService.navigateTo(route)
        }
    }
}
