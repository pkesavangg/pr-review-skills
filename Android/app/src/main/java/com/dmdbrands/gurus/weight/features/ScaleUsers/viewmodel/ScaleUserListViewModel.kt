package com.dmdbrands.gurus.weight.features.ScaleUsers.viewmodel

import androidx.lifecycle.viewModelScope
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
import com.dmdbrands.library.ggbluetooth.model.GGBTUser
import com.greatergoods.blewrapper.GGDeviceService
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import android.util.Log

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
      ScaleUserListIntent.RefreshUserList -> refreshUserList()
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
        val currentUserDisplayName = state.value.scale!!.preferences?.displayName

        ggDeviceService.getUsers(device) { response ->
          // Filter out the current connected user to prevent duplication
          val filteredUsers = if (currentUserDisplayName != null) {
            response.user.filter { user ->
              !user.name.equals(currentUserDisplayName, ignoreCase = true)
            }
          } else {
            response.user
          }

          Log.d(
            "ScaleUserList",
            "Current user: $currentUserDisplayName, Total users: ${response.user.size}, Filtered users: ${filteredUsers.size}",
          )
          handleIntent(ScaleUserListIntent.SetUserList(filteredUsers))
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
          performDeleteUser(user)
        },
      ),
    )
  }

  /**
   * Performs the actual user deletion by calling the GGDeviceService.
   * Similar to the implementation in BtWifiScaleSetupViewModel.
   *
   * @param user The user to delete from the scale
   */
  private fun performDeleteUser(user: GGBTUser) {
    val currentScale = state.value.scale
    if (currentScale == null) {
      showToast(ScaleUsersStrings.Toast.Error)
      return
    }

    viewModelScope.launch {
      try {
        dialogQueueService.showLoader(message = ScaleUsersStrings.Loading)

        // Create a device copy with the user's information for deletion
        // Similar to BtWifiScaleSetupViewModel's deleteUser implementation
        val deleteDevice = currentScale.copy(
          preferences = currentScale.preferences?.copy(
            displayName = user.name,
            shouldMeasureImpedance = user.isBodyMetricsEnabled,
          ),
          token = user.token,
        )

        // Delete the user account from the scale
        ggDeviceService.deleteAccount(deleteDevice.toGGBTDevice()) { response ->
          viewModelScope.launch {
            dialogQueueService.dismissLoader()
            loadScaleUsers()
          }
        }
      } catch (e: Exception) {
        dialogQueueService.dismissLoader()
        showToast(ScaleUsersStrings.Toast.Error)
      }
    }
  }

  /**
   * Refreshes the user list by reloading from the scale.
   * Public method that can be called from the UI to refresh the list.
   */
  fun refreshUserList() {
    loadScaleUsers()
  }

  private fun onBack() {
    navigateBack()
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
