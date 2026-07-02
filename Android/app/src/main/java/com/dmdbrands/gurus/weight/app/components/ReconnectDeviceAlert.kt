package com.dmdbrands.gurus.weight.app.components

import com.dmdbrands.gurus.weight.app.string.AppString
import com.dmdbrands.gurus.weight.features.common.model.DialogModel

class ReconnectScale {
  companion object {
    fun getMaxUserAlert(onConfirm: () -> Unit, onCancel: () -> Unit): DialogModel.Confirm {
      val string = AppString.Alert.MaxUser
      return DialogModel.Confirm(
        title = string.title,
        message = string.message,
        confirmText = AppString.Reconnect,
        cancelText = AppString.Cancel,
        onConfirm = onConfirm,
        onCancel = onCancel,
      )
    }

    fun getDuplicateUserAlert(onConfirm: () -> Unit, onCancel: () -> Unit): DialogModel.Confirm {
      val string = AppString.Alert.DuplicateUser
      return DialogModel.Confirm(
        title = string.title,
        message = string.message,
        confirmText = AppString.Reconnect,
        cancelText = AppString.Cancel,
        onConfirm = onConfirm,
        onCancel = onCancel,
      )
    }
  }
}
