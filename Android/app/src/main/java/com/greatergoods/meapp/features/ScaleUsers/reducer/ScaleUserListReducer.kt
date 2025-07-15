package com.greatergoods.meapp.features.ScaleUsers.reducer

import com.dmdbrands.library.ggbluetooth.model.GGBTUser
import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.domain.model.storage.Device
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.common.helper.form.FormValidations

/**
 * Controls for Scale Username form.
 */
data class ScaleUsernameFormControls(
  val username: FormControl<String>,
) {
  companion object {
    fun create() = ScaleUsernameFormControls(
      username = FormControl.create(
        initialValue = "",
        validators = listOf(
          FormValidations.required(),
        ),
      ),
    )
  }
}

/**
 * State for ScaleUserListScreen.
 */
data class ScaleUserListState(
  val scale: Device? = null,
  val scaleUserList: List<GGBTUser> = emptyList(),
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

  data class DeleteUser(
    val user: GGBTUser,
  ) : ScaleUserListIntent

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
          scaleUserList = intent.userList,
          isLoading = false,
        )
      }

      is ScaleUserListIntent.DeleteUser -> {
        val updatedList = state.scaleUserList.filter { it.token != intent.user.token }
        state.copy(
          scaleUserList = updatedList,
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
