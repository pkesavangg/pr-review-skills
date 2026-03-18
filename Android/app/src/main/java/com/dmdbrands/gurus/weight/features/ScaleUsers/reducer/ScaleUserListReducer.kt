package com.dmdbrands.gurus.weight.features.ScaleUsers.reducer

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.BtWifiScaleSetupStrings
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
data class ScaleUsernameFormControls(
  val username: FormControl<String>,
) {
  companion object {
    fun create(userList: List<GGBTUser> = persistentListOf()) = ScaleUsernameFormControls(
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
 * State for ScaleUserListScreen.
 */
@Stable
data class ScaleUserListState(
  val scale: Device? = null,
  val scaleUserList: ImmutableList<GGBTUser> = persistentListOf(),
  val isLoading: Boolean = false,
  val hasSetUsername: Boolean = false,
  val usernameForm: ScaleUsernameFormControls = ScaleUsernameFormControls.create(),
) : IReducer.State

/**
 * Intents for ScaleUserListScreen actions.
 */
sealed interface ScaleUserListIntent : IReducer.Intent {
  data class SetScale(
    val scale: Device,
    val hasSetUsername: Boolean = false,
  ) : ScaleUserListIntent

  data class SetUserList(
    val userList: List<GGBTUser>,
  ) : ScaleUserListIntent

  data class UpdateFormWithUserList(
    val userList: List<GGBTUser>,
  ) : ScaleUserListIntent

  data class DeleteUser(
    val user: GGBTUser,
  ) : ScaleUserListIntent

  data object RefreshUserList : ScaleUserListIntent

  data object Save : ScaleUserListIntent

  data object Back : ScaleUserListIntent
}

/**
 * Reducer for ScaleUserListScreen.
 */
class ScaleUserListReducer : IReducer<ScaleUserListState, ScaleUserListIntent> {
  override fun reduce(
    state: ScaleUserListState,
    intent: ScaleUserListIntent,
  ): ScaleUserListState? =
    when (intent) {
      is ScaleUserListIntent.SetScale -> {
        state.copy(
          scale = intent.scale,
          hasSetUsername = intent.hasSetUsername,
        )
      }

      is ScaleUserListIntent.SetUserList -> {
        state.copy(
          scaleUserList = intent.userList.toImmutableList(),
          isLoading = false,
        )
      }

      is ScaleUserListIntent.UpdateFormWithUserList -> {
        // Preserve the current username value and form state when updating form controls
        val currentUsername = state.usernameForm.username.value
        val newFormControls = ScaleUsernameFormControls.create(intent.userList)
        newFormControls.username.onValueChange(currentUsername)
        // Restore the dirty and touched states
        state.copy(
          usernameForm = newFormControls
        )
      }

      ScaleUserListIntent.Save -> {
        state.copy(
          isLoading = true,
        )
      }

      else -> state.copy()
    }
}
