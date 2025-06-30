package com.greatergoods.meapp.domain.interfaces

/**
 * Interface for dialog utility service that provides common dialog methods.
 */
interface IDialogUtility {
    /**
     * Shows a max account reached alert dialog.
     * 
     * @param isFromLanding Whether the alert is shown from the landing screen
     * @param onDismiss Optional callback when the dialog is dismissed
     */
    fun showMaxAccountAlert(
        isFromLanding: Boolean = false,
        onDismiss: (() -> Unit)? = null
    )
} 