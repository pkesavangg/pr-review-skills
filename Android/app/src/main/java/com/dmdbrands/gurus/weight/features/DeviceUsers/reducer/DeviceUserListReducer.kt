package com.dmdbrands.gurus.weight.features.DeviceUsers.reducer

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.features.DeviceSetup.strings.BtWifiScaleSetupStrings
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations
import com.dmdbrands.gurus.weight.features.common.helper.form.ValidationError
import com.dmdbrands.library.ggbluetooth.model.GGBTUser
import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Controls for Scale Username form.
 */
data class DeviceUsernameFormControls(
  val username: FormControl<String>,
) {
  companion object {
    fun create(userList: List<GGBTUser> = persistentListOf()) = DeviceUsernameFormControls(
      username = FormControl.create(
        initialValue = "",
        validators = listOf(
          FormValidations.required(),
          FormValidations.noWhiteSpace(),
          FormValidations.maxLength(20),
          FormValidations.scaleDisplayNameValidator(BtWifiScaleSetupStrings.DuplicateUser.UserErrorMessage),
          // Add duplicate name validator
        { value ->
            if (userList.isNotEmpty() && value.let { name ->
                userList.any { user -> user.name.equals(name, ignoreCase = true) }
              }) {
              ValidationError("DUPLICATE_NAME", BtWifiScaleSetupStrings.DuplicateUser.ErrorMessage)
            } else null
          }
        ),
      ),
    )
  }
}

/**
 * State for DeviceUserListScreen.
 */
@Stable
data class DeviceUserListState(
  val scale: Device? = null,
  val scaleUserList: ImmutableList<GGBTUser> = persistentListOf(),
  val isLoading: Boolean = false,
  val hasSetUsername: Boolean = false,
  val usernameForm: DeviceUsernameFormControls = DeviceUsernameFormControls.create(),
) : IReducer.State

/**
 * Intents for DeviceUserListScreen actions.
 */
sealed interface DeviceUserListIntent : IReducer.Intent {
  data class SetScale(
    val scale: Device,
    val hasSetUsername: Boolean = false,
  ) : DeviceUserListIntent

  data class SetUserList(
    val userList: List<GGBTUser>,
  ) : DeviceUserListIntent

  data class UpdateFormWithUserList(
    val userList: List<GGBTUser>,
  ) : DeviceUserListIntent

  data class DeleteUser(
    val user: GGBTUser,
  ) : DeviceUserListIntent

  data object RefreshUserList : DeviceUserListIntent

  data object Save : DeviceUserListIntent

  data object Back : DeviceUserListIntent
}

/**
 * Reducer for DeviceUserListScreen.
 */
class DeviceUserListReducer : IReducer<DeviceUserListState, DeviceUserListIntent> {
  override fun reduce(
    state: DeviceUserListState,
    intent: DeviceUserListIntent,
  ): DeviceUserListState? =
    when (intent) {
      is DeviceUserListIntent.SetScale -> {
        state.copy(
          scale = intent.scale,
          hasSetUsername = intent.hasSetUsername,
        )
      }

      is DeviceUserListIntent.SetUserList -> {
        state.copy(
          scaleUserList = intent.userList.toImmutableList(),
          isLoading = false,
        )
      }

      is DeviceUserListIntent.UpdateFormWithUserList -> {
        // Preserve the current username value and form state when updating form controls
        val currentUsername = state.usernameForm.username.value
        val newFormControls = DeviceUsernameFormControls.create(intent.userList)
        newFormControls.username.onValueChange(currentUsername)
        // Restore the dirty and touched states
        state.copy(
          usernameForm = newFormControls
        )
      }

      DeviceUserListIntent.Save -> {
        state.copy(
          isLoading = true,
        )
      }

      else -> state.copy()
    }
}
