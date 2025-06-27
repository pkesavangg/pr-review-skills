package com.greatergoods.meapp.features.common.service

import com.greatergoods.meapp.domain.interfaces.IDialogQueueService
import com.greatergoods.meapp.domain.interfaces.IDialogUtility
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.common.strings.AppPopupStrings
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility service for common dialogs and alerts used throughout the application.
 * Provides convenient methods for showing standard dialogs using the DialogQueueService.
 */
@Singleton
class DialogUtility @Inject constructor(
    private val dialogQueueService: IDialogQueueService
) : IDialogUtility {
    /**
     * Shows a max account reached alert dialog.
     *
     * @param isFromLanding Whether the alert is shown from the landing screen
     * @param onDismiss Optional callback when the dialog is dismissed
     */
    override fun showMaxAccountAlert(
        isFromLanding: Boolean,
        onDismiss: (() -> Unit)?
    ) {
        val message = if (isFromLanding) {
            AppPopupStrings.MaxAccountReachedAlert.Message2
        } else {
            AppPopupStrings.MaxAccountReachedAlert.Message1
        }

        val alert = DialogModel.Alert(
            title = AppPopupStrings.MaxAccountReachedAlert.Title,
            message = message,
            dismissText = AppPopupStrings.MaxAccountReachedAlert.ConfirmButton,
            onDismiss = onDismiss,
            alertPriority = 10 // High priority for account-related alerts
        )

        dialogQueueService.enqueue(alert)
    }

    override fun showAccountLoggedOutAlert(
        username: String,
        onDismiss: (() -> Unit)?
    ) {

        val alert = DialogModel.Alert(
            title = AppPopupStrings.MaxAccountReachedAlert.Title,
            message = AppPopupStrings.MaxAccountReachedAlert.Title,
            dismissText = AppPopupStrings.MaxAccountReachedAlert.ConfirmButton,
            onDismiss = onDismiss,
            alertPriority = 10 // High priority for account-related alerts
        )

        dialogQueueService.enqueue(alert)
    }
}
