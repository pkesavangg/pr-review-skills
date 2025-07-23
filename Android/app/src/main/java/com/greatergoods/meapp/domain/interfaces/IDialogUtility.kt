package com.greatergoods.meapp.domain.interfaces

import com.greatergoods.meapp.domain.model.storage.entry.Entry

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
    onRequest: () -> Unit,
    onDismiss: (() -> Unit)? = null
  )

  fun showEntrySyncPopup(
    entry: Entry,
    apiEntry: com.greatergoods.meapp.domain.model.api.entry.ScaleApiEntry,
    onEdit: () -> Unit,
    onSave: () -> Unit
  )
}
