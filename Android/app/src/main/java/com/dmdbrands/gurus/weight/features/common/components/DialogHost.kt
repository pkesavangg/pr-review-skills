package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.features.DeviceModeSettings.screens.BiaModal
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.AccucheckModal
import com.dmdbrands.gurus.weight.features.addDevice.screens.ModelNumberHelpDialog
import com.dmdbrands.gurus.weight.features.common.components.DialogType.HelpPopup
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.viewmodel.DialogQueueViewModel
import com.dmdbrands.gurus.weight.features.forgotPasswordDialog.screen.PasswordResetModal
import com.dmdbrands.gurus.weight.features.integration.components.AddHealthConnect
import com.dmdbrands.gurus.weight.features.integration.components.MultipleDeviceConnectionScreen
import com.dmdbrands.gurus.weight.features.integration.components.OutOfSyncScreen
import com.dmdbrands.gurus.weight.features.integration.components.RequestIntegrationModal
import com.dmdbrands.gurus.weight.features.deviceDetails.components.DeviceNameModal
import com.dmdbrands.gurus.weight.features.settings.components.AccountSwitchInfoModal

enum class DialogType {
  HeightPicker,
  HelpPopup,
  PasswordReset,
  RadioGroupPicker,
  SectionedRadioGroupPicker,
  AssignMeasurement,
  AccountSwitchInfoPopup,
  ModelNumberHelp,
  BiaModal,
  AccucheckModal,
  OutOfSyncModal,
  MultipleDeviceConnection,
  FinishConnect,
  DeviceName,
  AppsyncEntryPopup,
  SetGoalPopup,
  IAMFeedModal,
  GraphScrollHintModal,
  RequestIntegration,
}

@Composable
fun DialogHost() {
  val dialogQueueViewModel: DialogQueueViewModel = hiltViewModel()
  // Global dialog host
  DialogQueueHost(dialogQueueViewModel) { dialog ->
    when (dialog.contentKey) {
      DialogType.HeightPicker -> DialogHostHeightPicker(dialog, dialogQueueViewModel)
      HelpPopup -> DialogHostHelpPopup(dialog, dialogQueueViewModel)
      DialogType.RadioGroupPicker -> DialogHostRadioGroupPicker(dialog, dialogQueueViewModel)
      DialogType.SectionedRadioGroupPicker -> DialogHostSectionedRadioGroupPicker(dialog, dialogQueueViewModel)
      DialogType.PasswordReset -> DialogHostPasswordReset(dialog, dialogQueueViewModel)
      DialogType.AccountSwitchInfoPopup -> DialogHostAccountSwitchInfoPopup(dialog, dialogQueueViewModel)
      DialogType.ModelNumberHelp -> DialogHostModelNumberHelp(dialog, dialogQueueViewModel)
      DialogType.BiaModal -> DialogHostBiaModal(dialog, dialogQueueViewModel)
      DialogType.AccucheckModal -> DialogHostAccucheckModal(dialog, dialogQueueViewModel)
      DialogType.OutOfSyncModal -> DialogHostOutOfSyncModal(dialog, dialogQueueViewModel)
      DialogType.MultipleDeviceConnection -> DialogHostMultipleDeviceConnection(dialog, dialogQueueViewModel)
      DialogType.FinishConnect -> DialogHostFinishConnect(dialog, dialogQueueViewModel)
      DialogType.RequestIntegration -> DialogHostRequestIntegration(dialog, dialogQueueViewModel)
      DialogType.DeviceName -> DialogHostDeviceName(dialog, dialogQueueViewModel)
      DialogType.AppsyncEntryPopup -> DialogHostAppsyncEntryPopup(dialog, dialogQueueViewModel)
      DialogType.SetGoalPopup -> DialogHostSetGoalPopup(dialog, dialogQueueViewModel)
      DialogType.IAMFeedModal -> DialogHostIAMFeedModal(dialog, dialogQueueViewModel)
      DialogType.AssignMeasurement -> DialogHostAssignMeasurement(dialog, dialogQueueViewModel)
      DialogType.GraphScrollHintModal -> DialogHostGraphScrollHintModal(dialog, dialogQueueViewModel)
    }
  }
}

@Composable
private fun DialogHostHeightPicker(
  dialog: DialogModel.Custom,
  dialogQueueViewModel: DialogQueueViewModel,
) {
  // Extract params for unit and initial values
  AppHeightPickerModal(
    value = dialog.params.get("value") as HeightInput,
    confirmText = dialog.params["confirmText"] as String,
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

@Composable
private fun DialogHostHelpPopup(
  dialog: DialogModel.Custom,
  dialogQueueViewModel: DialogQueueViewModel,
) {
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

@Composable
private fun DialogHostRadioGroupPicker(
  dialog: DialogModel.Custom,
  dialogQueueViewModel: DialogQueueViewModel,
) {
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

@Composable
private fun DialogHostSectionedRadioGroupPicker(
  dialog: DialogModel.Custom,
  dialogQueueViewModel: DialogQueueViewModel,
) {
  val config = dialog.params["config"] as? SectionedRadioGroupModalConfig<*>
  val onConfirm = dialog.params["onConfirm"] as? (Map<String, Any?>) -> Unit
  val onCancel = dialog.params["onCancel"] as? (() -> Unit)
  if (config != null) {
    @Suppress("UNCHECKED_CAST")
    AppSectionedRadioGroupModal(
      title = config.title,
      subtitle = config.subtitle,
      sections = config.sections as List<RadioGroupSection<Any?>>,
      confirmText = config.confirmText,
      cancelText = config.cancelText,
      onCancel = {
        onCancel?.invoke()
        dialogQueueViewModel.dismissCurrent()
      },
      onOk = { selections ->
        onConfirm?.invoke(selections)
        dialogQueueViewModel.dismissCurrent()
      },
    )
  }
}

@Composable
private fun DialogHostPasswordReset(
  dialog: DialogModel.Custom,
  dialogQueueViewModel: DialogQueueViewModel,
) {
  val email = dialog.params["email"] as? String ?: ""
  PasswordResetModal(
    email = email,
    onDismiss = {
      dialog.onDismiss?.let { it() }
      dialogQueueViewModel.dismissCurrent()
    },
  )
}

@Composable
private fun DialogHostAccountSwitchInfoPopup(
  dialog: DialogModel.Custom,
  dialogQueueViewModel: DialogQueueViewModel,
) {
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

@Composable
private fun DialogHostModelNumberHelp(
  dialog: DialogModel.Custom,
  dialogQueueViewModel: DialogQueueViewModel,
) {
  ModelNumberHelpDialog(
    visible = true,
    onClose = {
      dialog.onDismiss?.let { it() }
      dialogQueueViewModel.dismissCurrent()
    },
  )
}

@Composable
private fun DialogHostBiaModal(
  dialog: DialogModel.Custom,
  dialogQueueViewModel: DialogQueueViewModel,
) {
  BiaModal(
    onClose = {
      dialog.onDismiss?.let { it() }
      dialogQueueViewModel.dismissCurrent()
    },
  )
}

@Composable
private fun DialogHostAccucheckModal(
  dialog: DialogModel.Custom,
  dialogQueueViewModel: DialogQueueViewModel,
) {
  AccucheckModal(
    onClose = {
      dialog.onDismiss?.let { it() }
      dialogQueueViewModel.dismissCurrent()
    },
  )
}

@Composable
private fun DialogHostOutOfSyncModal(
  dialog: DialogModel.Custom,
  dialogQueueViewModel: DialogQueueViewModel,
) {
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

@Composable
private fun DialogHostMultipleDeviceConnection(
  dialog: DialogModel.Custom,
  dialogQueueViewModel: DialogQueueViewModel,
) {
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

@Composable
private fun DialogHostFinishConnect(
  dialog: DialogModel.Custom,
  dialogQueueViewModel: DialogQueueViewModel,
) {
  AddHealthConnect(
    onClose = { dialogQueueViewModel.dismissCurrent() },
    onPrimaryAction = {
      dialog.onConfirm?.invoke(Unit)
      dialogQueueViewModel.dismissCurrent()
    },
  )
}

@Composable
private fun DialogHostRequestIntegration(
  dialog: DialogModel.Custom,
  dialogQueueViewModel: DialogQueueViewModel,
) {
  RequestIntegrationModal(
    onSend = { request ->
      dialog.onConfirm?.invoke(request)
      dialogQueueViewModel.dismissCurrent()
    },
    onDismiss = {
      dialog.onDismiss?.invoke()
      dialogQueueViewModel.dismissCurrent()
    },
  )
}

@Composable
private fun DialogHostDeviceName(
  dialog: DialogModel.Custom,
  dialogQueueViewModel: DialogQueueViewModel,
) {
  val scaleId = dialog.params["scaleId"] as? String ?: ""
  val accountId = dialog.params["accountId"] as? String
  DeviceNameModal(
    scaleId = scaleId,
    accountId = accountId,
    onDismiss = {
      dialog.onDismiss?.invoke()
      dialogQueueViewModel.dismissCurrent()
    },
  )
}

@Composable
private fun DialogHostAppsyncEntryPopup(
  dialog: DialogModel.Custom,
  dialogQueueViewModel: DialogQueueViewModel,
) {
  val entry = dialog.params["entry"] as ScaleEntry
  val onEdit = dialog.params["onEdit"] as (() -> Unit)
  val onSave = dialog.params["onSave"] as (() -> Unit)
  AppsyncEntryPopup(
    entry = entry,
    onEdit = onEdit,
    onSave = onSave,
  )
}

@Composable
private fun DialogHostSetGoalPopup(
  dialog: DialogModel.Custom,
  dialogQueueViewModel: DialogQueueViewModel,
) {
  val onSetGoal = dialog.params["onSetGoal"] as? (() -> Unit) ?: {}
  SetGoalPopup(
    onSetGoal = {
      onSetGoal()
      dialog.onConfirm?.invoke(Unit)
      dialogQueueViewModel.dismissCurrent()
    },
    onClose = {
      dialog.onDismiss?.invoke()
      dialogQueueViewModel.dismissCurrent()
    }
  )
}

@Composable
private fun DialogHostIAMFeedModal(
  dialog: DialogModel.Custom,
  dialogQueueViewModel: DialogQueueViewModel,
) {
  val feedItem = dialog.params["feedItem"] as com.greatergoods.ggInAppMessaging.domain.models.FeedItem

  IAMFeedModal(
    feedItem = feedItem,
    onDismiss = {
      dialog.onDismiss?.invoke()
      dialogQueueViewModel.dismissCurrent()
    },
    onAction = { actionType ->
      dialog.onConfirm?.invoke(actionType)
      dialogQueueViewModel.dismissCurrent()
    }
  )
}

@Composable
private fun DialogHostAssignMeasurement(
  dialog: DialogModel.Custom,
  dialogQueueViewModel: DialogQueueViewModel,
) {
  @Suppress("UNCHECKED_CAST")
  val babies = dialog.params["babies"] as List<com.dmdbrands.gurus.weight.domain.model.common.BabyProfile>
  val reading = dialog.params["reading"] as String
  val timestamp = dialog.params["timestamp"] as String
  val preSelectedBabyId = dialog.params["preSelectedBabyId"] as? String
  val onAssignNewBaby = dialog.params["onAssignNewBaby"] as? (() -> Unit)

  AssignMeasurementDialog(
    reading = reading,
    timestamp = timestamp,
    babies = babies,
    preSelectedBabyId = preSelectedBabyId,
    onAssign = { babyId ->
      dialog.onConfirm?.invoke(babyId)
      dialogQueueViewModel.dismissCurrent()
    },
    onAssignNewBaby = {
      onAssignNewBaby?.invoke()
      dialogQueueViewModel.dismissCurrent()
    },
    onDismiss = {
      dialog.onDismiss?.invoke()
      dialogQueueViewModel.dismissCurrent()
    },
  )
}

@Composable
private fun DialogHostGraphScrollHintModal(
  dialog: DialogModel.Custom,
  dialogQueueViewModel: DialogQueueViewModel,
) {
  com.dmdbrands.gurus.weight.features.dashboard.components.GraphScrollHintModal(
    onDismiss = {
      dialog.onDismiss?.invoke()
      dialogQueueViewModel.dismissCurrent()
    },
  )
}
