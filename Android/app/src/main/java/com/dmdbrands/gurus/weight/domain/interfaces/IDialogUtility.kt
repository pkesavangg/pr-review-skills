package com.dmdbrands.gurus.weight.domain.interfaces

import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry

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

  fun showAccountLoggedOutAlert(
    username: String,
    onDismiss: (() -> Unit)? = null
  )

  fun showModelNumberHelpDialog(
    onDismiss: (() -> Unit)? = null
  )

  fun permissionAlert(
    permissionType: String,
    isScaleSetupRequest: Boolean = false,
    onRequest: () -> Unit,
    onDismiss: (() -> Unit)? = null
  )

  fun showEntrySyncPopup(
    entry: Entry,
    apiEntry: com.dmdbrands.gurus.weight.domain.model.api.entry.ScaleApiEntry,
    onEdit: () -> Unit,
    onSave: () -> Unit
  )
}
