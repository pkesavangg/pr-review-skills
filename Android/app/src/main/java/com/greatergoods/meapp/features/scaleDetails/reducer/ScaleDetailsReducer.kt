package com.greatergoods.meapp.features.scaleDetails.reducer

import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.domain.model.storage.Device
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.common.helper.form.FormGroup
import com.greatergoods.meapp.features.common.helper.form.FormValidations
import com.greatergoods.meapp.features.scaleDetails.Enums.ScaleSettingSteps
import com.greatergoods.meapp.features.scaleDetails.strings.ScaleNameDialogStrings

/**
 * Controls for Scale Name Dialog form.
 */
data class ScaleNameDialogFormControls(
  val name: FormControl<String>,
) {
  companion object Companion {
    fun create() = ScaleNameDialogFormControls(
      name = FormControl.create(
        initialValue = "",
        validators = listOf(
          FormValidations.required(),
          FormValidations.maxLength(100, ScaleNameDialogStrings.ScalenameLabel),
        ),
      ),
    )
  }
}

/**
 * State for ScaleDetailsScreen.
 */
data class ScaleDetailsState(
  val scale: Device? = null,
  val scaleNameForm: FormGroup<ScaleNameDialogFormControls>,
  val permissions: GGPermissionStatusMap = mutableMapOf(),
  val settingsScreenStep: ScaleSettingSteps = ScaleSettingSteps.NONE,
) : IReducer.State

/**
 * Intents for ScaleDetailsScreen actions.
 */
sealed interface ScaleDetailsIntent : IReducer.Intent {
  data class SetScaleInfo(
    val scale: Device,
  ) : ScaleDetailsIntent

  object EditName : ScaleDetailsIntent

  object DeleteScale : ScaleDetailsIntent

  object OpenProductGuide : ScaleDetailsIntent

  object OpenScaleMode : ScaleDetailsIntent

  object OpenScaleUsers : ScaleDetailsIntent
  object OpenScaleDisplayMetrics : ScaleDetailsIntent
  object OpenWiFiSetup : ScaleDetailsIntent

  object Back : ScaleDetailsIntent
  object ShowScaleNameModal : ScaleDetailsIntent
  object UpdateScaleName : ScaleDetailsIntent
  data class OnCopyMacAddress(val isCopied: Boolean) : ScaleDetailsIntent
  data class SetScaleName(val name: String) : ScaleDetailsIntent
  data class SetPermissions(val permissions: GGPermissionStatusMap) : ScaleDetailsIntent
  data class SetSettingsScreenStep(val step: ScaleSettingSteps) : ScaleDetailsIntent
  data class RequestPermission(val permissionType: String) : ScaleDetailsIntent
}

/**
 * Reducer for ScaleDetailsScreen.
 */
class ScaleDetailsReducer : IReducer<ScaleDetailsState, ScaleDetailsIntent> {
  override fun reduce(
    state: ScaleDetailsState,
    intent: ScaleDetailsIntent,
  ): ScaleDetailsState? =
    when (intent) {
      is ScaleDetailsIntent.SetScaleInfo -> state.copy(scale = intent.scale)
      ScaleDetailsIntent.EditName -> state.copy()
      ScaleDetailsIntent.DeleteScale -> state.copy()
      ScaleDetailsIntent.OpenProductGuide -> state.copy()
      ScaleDetailsIntent.Back -> state.copy()
      is ScaleDetailsIntent.SetScaleName -> {
        val updatedForm = state.scaleNameForm.controls.name.apply {
          onValueChange(intent.name)
        }
        state.copy(scaleNameForm = FormGroup(ScaleNameDialogFormControls(updatedForm)))
      }

      is ScaleDetailsIntent.SetPermissions -> state.copy(permissions = intent.permissions)
      is ScaleDetailsIntent.SetSettingsScreenStep -> state.copy(settingsScreenStep = intent.step)
      else -> state.copy()
    }
}
