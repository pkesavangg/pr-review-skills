package com.dmdbrands.gurus.weight.features.DeviceSetup.manager

import com.dmdbrands.gurus.weight.core.config.AppConfig
import com.dmdbrands.gurus.weight.features.DeviceSetup.DeviceSetupConstants
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.BtWifiScaleSetupIntent
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.BtWifiScaleSetupState
import com.dmdbrands.gurus.weight.features.DeviceSetup.strings.DeviceSetupStrings
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Owns the modal / cosmetic-loader slice of [BtWifiScaleSetupViewModel] (MOB-1501).
 * Behaviour-preserving verbatim move.
 */
class BtWifiDialogPresenter(
    private val scope: CoroutineScope,
    private val getState: () -> BtWifiScaleSetupState,
    private val onIntent: (BtWifiScaleSetupIntent) -> Unit,
    private val enqueueDialog: (DialogModel) -> Unit,
    private val dismissCurrentDialog: () -> Unit,
    private val showLoader: (String) -> Unit,
    private val dismissLoader: () -> Unit,
    private val openInAppBrowser: (String) -> Unit,
) {

    fun openHelpModal() {
        enqueueDialog(
            DialogModel.Custom(
                contentKey = DialogType.HelpPopup,
                params = mapOf(
                    "showGuide" to true,
                    "onGuideClick" to {
                        openProductGuide()
                        dismissCurrentDialog()
                    },
                ),
            ),
        )
    }

    fun openAccucheckModel() {
        enqueueDialog(DialogModel.Custom(contentKey = DialogType.AccucheckModal, dismissOnBackPress = true))
    }

    fun openBiaModel() {
        enqueueDialog(
            DialogModel.Custom(
                contentKey = DialogType.BiaModal,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
        )
    }

    private fun openProductGuide() {
        val url = "${AppConfig.PRODUCT_URL}/${getState().sku}"
        openInAppBrowser(url)
    }

    /**
     * Shows a fixed-duration cosmetic "Saving..." loader after the user taps SAVE on a
     * Customize Settings sub-page, then signals the UI to scroll back to page 0.
     *
     * The delay is intentionally fixed (UX-only) and not tied to actual save completion —
     * the SAVE handler dispatches Set* intents which are pure reducer state mutations;
     * persistence to the device happens later when the user finalizes setup via
     * [BtWifiScaleSetupIntent.UpdateSettings]. If persistence is ever moved into this flow,
     * gate dismissal on completion instead of [DeviceSetupConstants.DELAY_AFTER_SAVE_MS].
     *
     * The [BtWifiScaleSetupIntent.SetIsSaving] flag prevents double-tap re-entry while the
     * loader is up (the SAVE button gates on `state.isSaving`). The try/finally guarantees
     * the flag, the loader, and the scroll-back signal all clear even if the dialog service
     * throws or the coroutine is cancelled. Fixes MA-2501.
     */
    fun showSavingLoader() {
        scope.launch {
            onIntent(BtWifiScaleSetupIntent.SetIsSaving(true))
            try {
                showLoader(DeviceSetupStrings.SaveScaleLoader)
                delay(DeviceSetupConstants.DELAY_AFTER_SAVE_MS)
            } finally {
                dismissLoader()
                onIntent(BtWifiScaleSetupIntent.SetIsSaving(false))
                onIntent(BtWifiScaleSetupIntent.ScrollToRootPage)
            }
        }
    }
}
