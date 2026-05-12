package com.dmdbrands.gurus.weight.features.ScaleUsers.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.api.device.toR4ScalePreferenceApiModel
import com.dmdbrands.gurus.weight.domain.model.storage.toGGBTDevice
import com.dmdbrands.gurus.weight.domain.model.storage.toGGDevicePreference
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.features.ScaleUsers.reducer.ScaleUserListIntent
import com.dmdbrands.gurus.weight.features.ScaleUsers.reducer.ScaleUserListReducer
import com.dmdbrands.gurus.weight.features.ScaleUsers.reducer.ScaleUserListState
import com.dmdbrands.gurus.weight.features.ScaleUsers.reducer.ScaleUsernameFormControls
import com.dmdbrands.gurus.weight.features.ScaleUsers.strings.ScaleUsersStrings
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
  companion object {
    private const val TAG = "ScaleUserListViewModel"
  }

  @AssistedFactory
  interface Factory {
    fun create(scaleId: String): ScaleUserListViewModel
  }

  override fun provideInitialState(): ScaleUserListState = ScaleUserListState(
    usernameForm = ScaleUsernameFormControls.create()
  )

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
          state.value.usernameForm.username.reset(scaleDevice.preferences?.displayName ?: "")
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
          handleIntent(ScaleUserListIntent.SetUserList(filteredUsers))
          handleIntent(ScaleUserListIntent.UpdateFormWithUserList(filteredUsers))
        }
      } catch (err: Exception) {
        AppLog.e(TAG, "Failed to load scale users", err)
        handleIntent(ScaleUserListIntent.SetUserList(emptyList()))
        handleIntent(ScaleUserListIntent.UpdateFormWithUserList(emptyList()))
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
        AppLog.d(TAG, "Updating scale username for scale: $scaleId")
        dialogQueueService.showLoader(message = ScaleUsersStrings.LoaderMessage)

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
                  showToast(ScaleUsersStrings.Toast.Success)
                  navigateBack()
                } else {
                  AppLog.e(TAG, "Failed to update scale preferences for scale: $scaleId")
                  showToast(ScaleUsersStrings.Toast.Error)
                  dialogQueueService.dismissLoader()
                }
              }
            }

            else -> {
              AppLog.e(TAG, "BLE account update returned unexpected response: $it")
              showToast(ScaleUsersStrings.Toast.Error)
              dialogQueueService.dismissLoader()
            }
          }
        }
      } catch (err: Exception) {
        AppLog.e(TAG, "Failed to update scale username", err)
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
      showToast(ScaleUsersStrings.Toast.Error)
      return
    }

    viewModelScope.launch {
      try {
        AppLog.d(TAG, "Deleting scale user: ${user.name} from scale: ${currentScale.id}")
        dialogQueueService.showLoader(message = ScaleUsersStrings.Loading)

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
