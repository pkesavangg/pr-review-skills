package com.dmdbrands.gurus.weight.features.ScaleUsers.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.library.ggbluetooth.model.GGBTUser
import com.greatergoods.blewrapper.GGDeviceService
import com.dmdbrands.gurus.weight.domain.model.api.device.toR4ScalePreferenceApiModel
import com.dmdbrands.gurus.weight.domain.model.storage.toGGBTDevice
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.features.ScaleMetricsSetting.Helper.ScaleMetricsHelper
import com.dmdbrands.gurus.weight.features.ScaleUsers.reducer.ScaleUserListIntent
import com.dmdbrands.gurus.weight.features.ScaleUsers.reducer.ScaleUserListReducer
import com.dmdbrands.gurus.weight.features.ScaleUsers.reducer.ScaleUserListState
import com.dmdbrands.gurus.weight.features.ScaleUsers.strings.ScaleUsersStrings
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.common.strings.AppPopupStrings
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch

@HiltViewModel(
  assistedFactory = ScaleUserListViewModel.Factory::class,
)
class ScaleUserListViewModel
@AssistedInject
constructor(
  private val ggDeviceService: GGDeviceService,
  private val deviceService: IDeviceService,
  @Assisted val scaleId: String,
) : BaseIntentViewModel<ScaleUserListState, ScaleUserListIntent>(
  reducer = ScaleUserListReducer(),
) {
  @AssistedFactory
  interface Factory {
    fun create(scaleId: String): ScaleUserListViewModel
  }

  override fun provideInitialState(): ScaleUserListState = ScaleUserListState()

  override fun handleIntent(intent: ScaleUserListIntent) {
    super.handleIntent(intent)
    when (intent) {
      ScaleUserListIntent.Back -> onBack()
      ScaleUserListIntent.Save -> updateScaleUsername()
      is ScaleUserListIntent.DeleteUser -> deleteUser(intent.user)
      else -> {}
    }
  }

  init {
    initScaleUserList()
  }

  private fun initScaleUserList() {
    viewModelScope.launch {
      deviceService.pairedScales.collect { devices ->
        val device = devices.find { it.id == scaleId }
        device?.let { scaleDevice ->
          state.value.usernameForm.username.onValueChange(scaleDevice.preferences?.displayName ?: "")
          handleIntent(ScaleUserListIntent.SetScale(scaleDevice, hasSetUsername = true))
          loadScaleUsers()
        }
      }
    }
  }

  private fun loadScaleUsers() {
    if (state.value.scale == null) {
      handleIntent(ScaleUserListIntent.SetUserList(emptyList()))
      showToast(ScaleUsersStrings.Toast.LoadError)
      return
    }
    viewModelScope.launch {
      try {
        val device = state.value.scale!!.toGGBTDevice()
        ggDeviceService.getUsers(device) {
          handleIntent(ScaleUserListIntent.SetUserList(it.user))
        }
      } catch (err: Exception) {
        handleIntent(ScaleUserListIntent.SetUserList(emptyList()))
        showToast(ScaleUsersStrings.Toast.LoadError)
      }
    }
  }

  private fun updateScaleUsername() {
    val currentState = state.value
    val scale = currentState.scale
    if (scale == null || !state.value.usernameForm.username.isValueValid()) {
      showToast(ScaleUsersStrings.Toast.Error)
      return
    }


    viewModelScope.launch {
      try {
        dialogQueueService.showLoader(message = ScaleUsersStrings.LoaderMessage)

        val preferences =
          scale.preferences?.copy(
            displayName = state.value.usernameForm.username.value,
          ) ?: ScaleMetricsHelper.getDefaultPreference(state.value.usernameForm.username.value)
        val success = deviceService.updateScalePreferences(scaleId, preferences.toR4ScalePreferenceApiModel())
        // ggDeviceService.updateSettings(updatedScale)

        if (success) {
          dialogQueueService.dismissLoader()
          showToast(ScaleUsersStrings.Toast.Success)
          navigateBack()
        } else {
          showToast(ScaleUsersStrings.Toast.Error)
        }
      } catch (err: Exception) {
        dialogQueueService.dismissLoader()
        showToast(ScaleUsersStrings.Toast.Error)
      }
    }
  }

  private fun deleteUser(user: GGBTUser) {
    dialogQueueService.enqueue(
      DialogModel.Confirm(
        title = ScaleUsersStrings.DeleteUserAlert.Title,
        message = ScaleUsersStrings.DeleteUserAlert.Message(user.name),
        confirmText = ScaleUsersStrings.DeleteUserAlert.Delete,
        cancelText = ScaleUsersStrings.DeleteUserAlert.Back,
        onConfirm = {
          // Delete user and update the list
          state.value.usernameForm.username.reset()
        },
      ),
    )
  }

  private fun onBack() {
    if (state.value.usernameForm.username.isValueValid()) {
      dialogQueueService.enqueue(
        DialogModel.Confirm(
          title = AppPopupStrings.UnsavedExitPopup.Title,
          message = AppPopupStrings.UnsavedExitPopup.Message,
          confirmText = AppPopupStrings.UnsavedExitPopup.Leave,
          cancelText = AppPopupStrings.UnsavedExitPopup.Cancel,
          onConfirm = {
            navigateBack()
            state.value.usernameForm.username.reset()
          },
        ),
      )
    } else {
      navigateBack()
    }
  }

  private fun showDeleteUserAlert() {
  }

  private fun navigateBack() {
    viewModelScope.launch {
      navigationService.navigateBack()
    }
  }

  private fun showToast(message: String) {
    dialogQueueService.showToast(
      Toast(
        title = null,
        message = message,
        action = null,
      ),
    )
  }
}
