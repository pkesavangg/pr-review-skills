package com.greatergoods.meapp.features.common.components

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.libs.appsync.model.AppSyncResult
import com.greatergoods.meapp.features.ScaleModeSettings.screens.BiaModal
import com.greatergoods.meapp.features.ScaleSetup.components.AccucheckModal
import com.greatergoods.meapp.features.addScale.screens.ModelNumberHelpDialog
import com.greatergoods.meapp.features.common.components.DialogType.HelpPopup
import com.greatergoods.meapp.features.common.viewmodel.DialogQueueViewModel
import com.greatergoods.meapp.features.forgotPasswordDialog.screen.PasswordResetModal
import com.greatergoods.meapp.features.integration.components.AddHealthConnect
import com.greatergoods.meapp.features.integration.components.MultipleDeviceConnectionScreen
import com.greatergoods.meapp.features.integration.components.OutOfSyncScreen
import com.greatergoods.meapp.features.scaleDetails.components.ScaleNameModal
import com.greatergoods.meapp.features.settings.components.AccountSwitchInfoModal

enum class DialogType {
  HeightPicker,
  HelpPopup,
  PasswordReset,
  RadioGroupPicker,
  AccountSwitchInfoPopup,
  ModelNumberHelp,
  BiaModal,
  AccucheckModal,
  OutOfSyncModal,
  MultipleDeviceConnection,
  FinishConnect,
  ScaleName,
  AppsyncEntryPopup
}

@Composable
fun DialogHost() {
  val dialogQueueViewModel: DialogQueueViewModel = hiltViewModel()
  // Global dialog host
  DialogQueueHost(dialogQueueViewModel) { dialog ->
    when (dialog.contentKey) {
      // Custom dialog for height picker
      DialogType.HeightPicker -> {
        // Extract params for unit and initial values
        AppHeightPickerModal(
          value = dialog.params.get("value") as HeightInput,
          onCancel = {
            dialog.onDismiss?.let { it() }
            dialogQueueViewModel.dismissCurrent()
          },
          onOk = { data ->
            // You can add callback logic here to return the selected value
            dialog.onConfirm?.let { it(data) }
            dialogQueueViewModel.dismissCurrent()
          },
        )
      }

      HelpPopup -> {
        // Custom dialog for help popup
        val showGuide = dialog.params["showGuide"] as? Boolean ?: false
        val onGuideClick = dialog.params["onGuideClick"] as? (() -> Unit)
        AppHelpModal(
          onClose = {
            dialog.onDismiss?.let { it() }
            dialogQueueViewModel.dismissCurrent()
          },
          showGuide = showGuide,
          onGuideClick = if (showGuide && onGuideClick != null) {
            {
              onGuideClick.invoke()
            }
          } else {
            null
          },
        )
      }

      DialogType.RadioGroupPicker -> {
        // Custom dialog for radio group picker
        val config = dialog.params["config"] as? RadioGroupModalConfig<*>
        val onConfirm = dialog.params["onConfirm"] as? (Any?) -> Unit
        val onCancel = dialog.params["onCancel"] as? (() -> Unit)

        if (config != null) {
          AppRadioGroupModal(
            config = config as RadioGroupModalConfig<Any>,
            onCancel = {
              onCancel?.invoke()
              dialogQueueViewModel.dismissCurrent()
            },
            onOk = { selectedValue ->
              onConfirm?.invoke(selectedValue)
              dialogQueueViewModel.dismissCurrent()
            },
          )
        }
      }

      DialogType.PasswordReset -> {
        val email = dialog.params["email"] as? String ?: ""
        PasswordResetModal(
          email = email,
          onDismiss = {
            dialog.onDismiss?.let { it() }
            dialogQueueViewModel.dismissCurrent()
          },
        )
      }

      DialogType.AccountSwitchInfoPopup -> {
        val userInitial = dialog.params["userInitial"] as? String ?: "U"
        val onAddAccount = dialog.params["onAddAccount"] as? (() -> Unit) ?: {}
        AccountSwitchInfoModal(
          userInitial = userInitial,
          onAddAccount = {
            dialog.onDismiss?.let { it() }
            onAddAccount()
          },
          onClose = {
            dialog.onDismiss?.let { it() }
            dialogQueueViewModel.dismissCurrent()
          },
        )
      }

      DialogType.ModelNumberHelp -> {
        ModelNumberHelpDialog(
          visible = true,
          onClose = {
            dialog.onDismiss?.let { it() }
            dialogQueueViewModel.dismissCurrent()
          },
        )
      }

      DialogType.BiaModal -> {
        BiaModal(
          onClose = {
            dialog.onDismiss?.let { it() }
            dialogQueueViewModel.dismissCurrent()
          },
        )
      }

      DialogType.AccucheckModal -> {
        AccucheckModal(
          onClose = {
            dialog.onDismiss?.let { it() }
            dialogQueueViewModel.dismissCurrent()
          },
        )
      }

      DialogType.OutOfSyncModal -> {
        val secondaryAction = dialog.params["secondaryAction"] as? (() -> Unit)
        OutOfSyncScreen(
          onPrimaryAction = {
            dialog.onConfirm?.invoke(Unit)
            dialogQueueViewModel.dismissCurrent()
          },
          onSecondaryAction = {
            secondaryAction?.invoke()
            dialogQueueViewModel.dismissCurrent()
          },
          onClose = {
            dialog.onDismiss?.invoke()
            dialogQueueViewModel.dismissCurrent()
          },
        )
      }

      DialogType.MultipleDeviceConnection -> {
        MultipleDeviceConnectionScreen(
          onPrimaryAction = {
            dialog.onConfirm?.invoke(Unit)
            dialogQueueViewModel.dismissCurrent()
          },
          onSecondaryAction = {
            dialog.onDismiss?.invoke()
            dialogQueueViewModel.dismissCurrent()
          },
          onClose = {
            dialog.onDismiss?.invoke()
            dialogQueueViewModel.dismissCurrent()
          },
        )
      }

      DialogType.FinishConnect -> {
        AddHealthConnect(
          onClose = { dialogQueueViewModel.dismissCurrent() },
          onPrimaryAction = {
            dialog.onConfirm?.invoke(Unit)
            dialogQueueViewModel.dismissCurrent()
          },
        )
      }

      DialogType.ScaleName -> {
        val scaleId = dialog.params["scaleId"] as? String ?: ""
        ScaleNameModal(
          scaleId = scaleId,
          onDismiss = {
            dialog.onDismiss?.let { it() }
            dialogQueueViewModel.dismissCurrent()
          },
        )
      }

      DialogType.AppsyncEntryPopup -> {
        val entry = dialog.params["entry"] as AppSyncResult
        val onEdit = dialog.params["onEdit"] as (() -> Unit)
        val onSave = dialog.params["onSave"] as (() -> Unit)
        AppsyncEntryPopup(
          entry = entry,
          onEdit = onEdit,
          onSave = onSave,
        )
      }
    }
  }
}
