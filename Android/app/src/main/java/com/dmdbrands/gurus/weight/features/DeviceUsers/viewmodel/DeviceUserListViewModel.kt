package com.dmdbrands.gurus.weight.features.DeviceUsers.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.api.device.toR4ScalePreferenceApiModel
import com.dmdbrands.gurus.weight.domain.model.storage.toGGBTDevice
import com.dmdbrands.gurus.weight.domain.model.storage.toGGDevicePreference
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.features.DeviceUsers.reducer.DeviceUserListIntent
import com.dmdbrands.gurus.weight.features.DeviceUsers.reducer.DeviceUserListReducer
import com.dmdbrands.gurus.weight.features.DeviceUsers.reducer.DeviceUserListState
import com.dmdbrands.gurus.weight.features.DeviceUsers.reducer.DeviceUsernameFormControls
import com.dmdbrands.gurus.weight.features.DeviceUsers.strings.DeviceUsersStrings
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.strings.AppPopupStrings
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.library.ggbluetooth.enums.GGUserActionResponseType
import com.dmdbrands.library.ggbluetooth.model.GGBTUser
import com.greatergoods.blewrapper.GGDeviceService
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch

@HiltViewModel(
  assistedFactory = DeviceUserListViewModel.Factory::class,
)
class DeviceUserListViewModel
@AssistedInject
constructor(
  private val ggDeviceService: GGDeviceService,
  private val deviceService: IDeviceService,
  @Assisted val scaleId: String,
) : BaseIntentViewModel<DeviceUserListState, DeviceUserListIntent>(
  reducer = DeviceUserListReducer(),
) {
  companion object {
    private const val TAG = "DeviceUserListViewModel"
  }

  @AssistedFactory
  interface Factory {
    fun create(scaleId: String): DeviceUserListViewModel
  }

  override fun provideInitialState(): DeviceUserListState = DeviceUserListState(
    usernameForm = DeviceUsernameFormControls.create()
  )

  override fun handleIntent(intent: DeviceUserListIntent) {
    super.handleIntent(intent)
    when (intent) {
      DeviceUserListIntent.Back -> onBack()
      DeviceUserListIntent.Save -> updateScaleUsername()
      is DeviceUserListIntent.DeleteUser -> deleteUser(intent.user)
      DeviceUserListIntent.RefreshUserList -> refreshUserList()
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
          state.value.usernameForm.username.reset(scaleDevice.preferences?.displayName ?: "")
          handleIntent(DeviceUserListIntent.SetScale(scaleDevice, hasSetUsername = true))
          loadScaleUsers()
        }
      }
    }
  }

  private fun loadScaleUsers() {
    if (state.value.scale == null) {
      handleIntent(DeviceUserListIntent.SetUserList(emptyList()))
      showToast(DeviceUsersStrings.Toast.LoadError)
      return
    }
    viewModelScope.launch {
      try {
        AppLog.d(TAG, "Loading scale users for scale: ${state.value.scale?.id}")
        val scale = state.value.scale ?: return@launch
        val device = scale.toGGBTDevice()
        val currentUserDisplayName = scale.preferences?.displayName

        ggDeviceService.getUsers(device) { response ->
          val filteredUsers = if (currentUserDisplayName != null) {
            response.user.filter { user ->
              !user.name.equals(currentUserDisplayName, ignoreCase = true)
            }
          } else {
            response.user
          }
          AppLog.i(TAG, "Scale users loaded successfully: ${filteredUsers.size} user(s)")
          handleIntent(DeviceUserListIntent.SetUserList(filteredUsers))
          handleIntent(DeviceUserListIntent.UpdateFormWithUserList(filteredUsers))
        }
      } catch (err: Exception) {
        AppLog.e(TAG, "Failed to load scale users", err)
        handleIntent(DeviceUserListIntent.SetUserList(emptyList()))
        handleIntent(DeviceUserListIntent.UpdateFormWithUserList(emptyList()))
        showToast(DeviceUsersStrings.Toast.LoadError)
      }
    }
  }

  private fun updateScaleUsername() {
    val currentState = state.value
    val scale = currentState.scale
    if (scale == null || !state.value.usernameForm.username.isValueValid()) {
      showToast(DeviceUsersStrings.Toast.Error)
      return
    }


    viewModelScope.launch {
      try {
        AppLog.d(TAG, "Updating scale username for scale: $scaleId")
        dialogQueueService.showLoader(message = DeviceUsersStrings.LoaderMessage)

        val preferences = requireNotNull(
          scale.preferences?.toR4ScalePreferenceApiModel()?.copy(
            displayName = state.value.usernameForm.username.value,
          )
        ) { "Scale preferences are null; cannot update username" }

        val updatedScalePreference = scale.preferences.copy(
          displayName = state.value.usernameForm.username.value,
        ).toGGDevicePreference()
        val updatedScale = scale.toGGBTDevice().copy(preference = updatedScalePreference)

        ggDeviceService.updateAccount(
          updatedScale,
        ) {
          when (it) {
            GGUserActionResponseType.CREATION_COMPLETED, GGUserActionResponseType.UPDATE_COMPLETED -> {
              viewModelScope.launch {
                val success = deviceService.updateScalePreferences(scaleId, preferences)
                if (success) {
                  AppLog.i(TAG, "Scale username updated successfully for scale: $scaleId")
                  dialogQueueService.dismissLoader()
                  deviceService.syncDevices()
                  showToast(DeviceUsersStrings.Toast.Success)
                  navigateBack()
                } else {
                  AppLog.e(TAG, "Failed to update scale preferences for scale: $scaleId")
                  showToast(DeviceUsersStrings.Toast.Error)
                  dialogQueueService.dismissLoader()
                }
              }
            }

            else -> {
              AppLog.e(TAG, "BLE account update returned unexpected response: $it")
              showToast(DeviceUsersStrings.Toast.Error)
              dialogQueueService.dismissLoader()
            }
          }
        }
      } catch (err: Exception) {
        AppLog.e(TAG, "Failed to update scale username", err)
        dialogQueueService.dismissLoader()
        showToast(DeviceUsersStrings.Toast.Error)
      }
    }
  }

  private fun deleteUser(user: GGBTUser) {
    dialogQueueService.enqueue(
      DialogModel.Confirm(
        title = DeviceUsersStrings.DeleteUserAlert.Title,
        message = DeviceUsersStrings.DeleteUserAlert.Message(user.name),
        confirmText = DeviceUsersStrings.DeleteUserAlert.Delete,
        cancelText = DeviceUsersStrings.DeleteUserAlert.Back,
        primaryActionType = ButtonType.ErrorText,
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
      showToast(DeviceUsersStrings.Toast.Error)
      return
    }

    viewModelScope.launch {
      try {
        AppLog.d(TAG, "Deleting scale user: ${user.name} from scale: ${currentScale.id}")
        dialogQueueService.showLoader(message = DeviceUsersStrings.Loading)

        val deleteDevice = currentScale.copy(
          preferences = currentScale.preferences?.copy(
            displayName = user.name,
            shouldMeasureImpedance = user.isBodyMetricsEnabled,
          ),
          token = user.token,
        )

        ggDeviceService.deleteAccount(deleteDevice.toGGBTDevice()) { response ->
          viewModelScope.launch {
            AppLog.i(TAG, "Scale user deleted successfully: ${user.name}")
            dialogQueueService.dismissLoader()
            loadScaleUsers()
          }
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to delete scale user: ${user.name}", e)
        dialogQueueService.dismissLoader()
        showToast(DeviceUsersStrings.Toast.Error)
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
    // Check if username has been changed
    val originalUsername = state.value.scale?.preferences?.displayName ?: ""
    val currentUsername = state.value.usernameForm.username.value
    val hasChanges = currentUsername != originalUsername && currentUsername.isNotEmpty()

    if (hasChanges) {
      dialogQueueService.enqueue(
        DialogModel.Confirm(
          title = AppPopupStrings.UnsavedExitPopup.Title,
          message = AppPopupStrings.UnsavedExitPopup.Message,
          confirmText = AppPopupStrings.UnsavedExitPopup.Leave,
          cancelText = AppPopupStrings.UnsavedExitPopup.Cancel,
          onConfirm = {
            navigateBack()
            initScaleUserList()
          },
        ),
      )
    } else {
      navigateBack()
    }
  }

  private fun navigateBack() {
    viewModelScope.launch {
      navigationService.navigateBack()
    }
  }

  private fun showToast(message: String) {
    dialogQueueService.showToast(
      Toast.Simple(
        title = null,
        message = message,
        action = null,
      ),
    )
  }
}
