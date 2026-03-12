package com.dmdbrands.gurus.weight.features.settings.manager

import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import com.dmdbrands.gurus.weight.domain.enums.ActivityLevel
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.api.user.BodyCompUpdateRequest
import com.dmdbrands.gurus.weight.domain.model.api.user.ProfileUpdateRequest
import com.dmdbrands.gurus.weight.domain.model.common.Gender
import com.dmdbrands.gurus.weight.domain.services.BodyCompUpdateType
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IBodyCompositionService
import com.dmdbrands.gurus.weight.domain.services.IUserSettingsService
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.common.components.HeightInput
import com.dmdbrands.gurus.weight.features.common.components.RadioButtonOption
import com.dmdbrands.gurus.weight.features.common.components.showRadioGroupModal
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.settings.strings.RadioGroupModalStrings
import com.dmdbrands.gurus.weight.features.settings.viewmodel.SettingsIntent
import com.dmdbrands.gurus.weight.features.settings.viewmodel.SettingsState
import com.dmdbrands.gurus.weight.features.weightless.helper.WeightlessHelper
import com.dmdbrands.library.ggbluetooth.enums.GGUserActionResponseType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

interface IProfileSettingsManager {
  fun observeUserProfile(
    scope: CoroutineScope,
    dispatch: (SettingsIntent) -> Unit,
  )

  fun onBiologicalSexClick(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
  )

  fun onActivityLevelClick(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
  )

  fun onHeightClick(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
  )

  fun onGoalSettingClick(scope: CoroutineScope)

  fun onWeightlessClick(scope: CoroutineScope)

  fun onShowWeightlessModal(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
  )

  fun onStreakUpdate(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
    isStreakOn: Boolean,
  )

  fun showAccountSwitchInfoModal(scope: CoroutineScope)

  fun dismissAccountSwitchInfoModal()

  fun getWeightlessDisplayText(state: SettingsState): String
}

class ProfileSettingsManager
@Inject
constructor(
  private val accountService: IAccountService,
  private val bodyCompositionService: IBodyCompositionService,
  private val userDataStore: UserDataStore,
  private val userSettingsService: IUserSettingsService,
  private val dialogQueueService: IDialogQueueService,
  private val navigationService: IAppNavigationService,
  private val scaleSettingsManager: IScaleSettingsManager,
) : IProfileSettingsManager {
  companion object {
    private const val TAG = "ProfileSettingsManager"
  }

  override fun observeUserProfile(
    scope: CoroutineScope,
    dispatch: (SettingsIntent) -> Unit,
  ) {
    scope.launch {
      accountService.loggedInAccountsFlow.collect {
        val account = accountService.getCurrentAccount()
        val hasMultipleAccounts = it.size > 1
        dispatch(SettingsIntent.UpdateAccount(account, hasMultipleAccounts))
      }
    }
  }

  override fun onBiologicalSexClick(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
  ) {
    AppLog.d(TAG, "Biological sex clicked")
    showBiologicalSexModal(scope, stateProvider)
  }

  override fun onActivityLevelClick(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
  ) {
    AppLog.d(TAG, "Activity level clicked")
    showActivityLevelModal(scope, stateProvider)
  }

  override fun onHeightClick(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
  ) {
    AppLog.d(TAG, "Height clicked")
    showHeightModal(scope, stateProvider)
  }

  override fun onGoalSettingClick(scope: CoroutineScope) {
    AppLog.d(TAG, "Goal setting clicked")
    scope.launch {
      navigationService.navigateTo(AppRoute.AccountSettings.Goal)
    }
  }

  override fun onWeightlessClick(scope: CoroutineScope) {
    AppLog.d(TAG, "Weightless clicked")
    scope.launch {
      navigationService.navigateTo(AppRoute.AccountSettings.Weightless)
    }
  }

  override fun onShowWeightlessModal(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
  ) {
    val currentAccount = stateProvider().account
    currentAccount?.isWeightlessOn ?: false

    showRadioGroupModal(
      dialogService = dialogQueueService,
      title = "Weightless Mode",
      options =
        listOf(
          RadioButtonOption("true", "On"),
          RadioButtonOption("false", "Off"),
        ),
      selectedItem = stateProvider().account?.isWeightlessOn ?: false,
      onConfirm = { selectedWeightless ->
        selectedWeightless?.let { weightlessValue ->
          val isWeightlessOn = weightlessValue.toString().toBoolean()
          onWeightlessUpdate(scope, stateProvider, isWeightlessOn)
        }
      },
      onCancel = {
        AppLog.d(TAG, "Weightless mode selection cancelled")
      },
    )
  }

  override fun onStreakUpdate(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
    isStreakOn: Boolean,
  ) {
    val currentAccount = stateProvider().account
    if (currentAccount?.isStreakOn == isStreakOn) {
      AppLog.d(TAG, "Streak mode is already set to $isStreakOn, no update needed")
      return
    }

    dialogQueueService.showLoader("Updating streak mode...")
    scope.launch {
      try {
        userSettingsService.toggleStreakSetting(isStreakOn = isStreakOn)
        AppLog.i(TAG, "Successfully updated streak mode")
      } catch (e: Exception) {
        AppLog.e(TAG, "Error updating streak mode", e)
      } finally {
        dialogQueueService.dismissLoader()
      }
    }
  }

  override fun showAccountSwitchInfoModal(scope: CoroutineScope) {
    scope.launch {
      val hasShown = userDataStore.hasShownAccountSwitchInfoModalForDevice()
      if (hasShown) return@launch
      val activeAccount = accountService.getCurrentAccount()

      userDataStore.setAccountSwitchInfoModalShownForDevice(true)

      dialogQueueService.enqueue(
        DialogModel.Custom(
          contentKey = DialogType.AccountSwitchInfoPopup,
          params =
            mapOf(
              "userInitial" to (activeAccount?.firstName?.firstOrNull()?.toString() ?: "U"),
              "onAddAccount" to {
                dismissAccountSwitchInfoModal()
                scope.launch {
                  navigationService.navigateTo(AppRoute.AccountSettings.MyAccounts)
                }
              },
            ),
          onDismiss = {
            dismissAccountSwitchInfoModal()
          },
        ),
      )
    }
  }

  override fun dismissAccountSwitchInfoModal() {
    dialogQueueService.dismissCurrent()
  }

  override fun getWeightlessDisplayText(state: SettingsState): String {
    val account = state.account
    return if (account?.isWeightlessOn == true) {
      val weightlessWeight = account.weightlessWeight
      if (weightlessWeight != null) {
        val displayWeight =
          WeightlessHelper.processStoredWeightToDisplay(weightlessWeight.toDouble(), account.weightUnit)
        "On - ${displayWeight / 10}"
      } else {
        "On"
      }
    } else {
      "Off"
    }
  }

  private fun showBiologicalSexModal(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
  ) {
    showRadioGroupModal(
      dialogService = dialogQueueService,
      title = RadioGroupModalStrings.Titles.BiologicalSex,
      options =
        listOf(
          RadioButtonOption(Gender.MALE.name.lowercase(), RadioGroupModalStrings.BiologicalSex.Male),
          RadioButtonOption(Gender.FEMALE.name.lowercase(), RadioGroupModalStrings.BiologicalSex.Female),
        ),
      selectedItem = stateProvider().account?.gender,
      confirmText = RadioGroupModalStrings.Button.Save,
      onConfirm = { selectedSex ->
        AppLog.d(TAG, "Biological sex modal onConfirm called with: $selectedSex")
        selectedSex?.let { gender ->
          onBiologicalSexUpdate(scope, stateProvider, gender.toString())
        }
      },
      onCancel = {
        AppLog.d(TAG, "Biological sex selection cancelled")
      },
    )
  }

  private fun onBiologicalSexUpdate(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
    gender: String,
  ) {
    val currentAccount = stateProvider().account
    if (currentAccount == null) {
      AppLog.e(TAG, "No active account found for biological sex update")
      return
    }
    if (currentAccount.gender == gender) {
      AppLog.d(TAG, "Gender is already set to $gender, no update needed")
      return
    }

    dialogQueueService.showLoader("Loading...")
    scope.launch {
      try {
        val updatedCurrentProfile =
          ProfileUpdateRequest(
            id = currentAccount.id,
            firstName = currentAccount.firstName,
            lastName = currentAccount.lastName,
            email = currentAccount.email,
            dob = currentAccount.dob,
            gender = gender,
            zipcode = currentAccount.zipcode,
          )

        val updatedProfile = currentAccount.toGGBTUserProfile().copy(sex = gender)
        val scaleResult = scaleSettingsManager.updateR4Profile(updatedProfile)
        AppLog.d(TAG, "Scale result: $scaleResult")
        scaleSettingsManager.handleScaleUpdateResult(scaleResult)

        accountService.updateProfile(updatedCurrentProfile, isFromProfile = false, showToast = false)
        AppLog.i(TAG, "Successfully updated biological sex")
      } catch (e: Exception) {
        dialogQueueService.dismissLoader()
        AppLog.e(TAG, "Error updating biological sex", e)
      } finally {
        dialogQueueService.dismissLoader()
      }
    }
  }

  private fun showActivityLevelModal(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
  ) {
    showRadioGroupModal(
      dialogService = dialogQueueService,
      title = RadioGroupModalStrings.Titles.ActivityLevel,
      options =
        listOf(
          RadioButtonOption(
            ActivityLevel.NORMAL.name.lowercase(),
            RadioGroupModalStrings.ActivityLevel.Normal,
          ),
          RadioButtonOption(
            ActivityLevel.ATHLETE.name.lowercase(),
            RadioGroupModalStrings.ActivityLevel.Athlete,
          ),
        ),
      selectedItem = stateProvider().account?.activityLevel,
      confirmText = RadioGroupModalStrings.Button.Save,
      onConfirm = { selectedActivityLevel ->
        selectedActivityLevel?.let { activityLevel ->
          onActivityLevelUpdate(scope, stateProvider, activityLevel.toString())
        }
      },
      onCancel = {
      },
    )
  }

  private fun onActivityLevelUpdate(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
    activityLevel: String,
  ) {
    val currentAccount = stateProvider().account
    if (currentAccount == null) {
      AppLog.e(TAG, "No active account found for activity level update")
      return
    }
    if (currentAccount.activityLevel == activityLevel) {
      AppLog.d(TAG, "Activity level is already set to $activityLevel, no update needed")
      return
    }

    dialogQueueService.showLoader("Loading...")
    scope.launch {
      try {
        val bodyComposition =
          BodyCompUpdateRequest(
            height = currentAccount.height ?: 1700,
            activityLevel = activityLevel,
            weightUnit = currentAccount.weightUnit.value,
          )

        val updatedProfile =
          currentAccount.toGGBTUserProfile().copy(isAthlete = (activityLevel == ActivityLevel.ATHLETE.name.lowercase()))
        val scaleResult = scaleSettingsManager.updateR4Profile(updatedProfile)
        AppLog.d(TAG, "Scale result: $scaleResult")
        scaleSettingsManager.handleScaleUpdateResult(scaleResult)

        bodyCompositionService.updateBodyComposition(BodyCompUpdateType.ACTIVITY_LEVEL, bodyComposition)
        AppLog.i(TAG, "Successfully updated activity level")
      } catch (e: Exception) {
        AppLog.e(TAG, "Error updating activity level", e)
      } finally {
        dialogQueueService.dismissLoader()
      }
    }
  }

  private fun showHeightModal(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
  ) {
    val currentAccount = stateProvider().account
    if (currentAccount == null) {
      AppLog.e(TAG, "No active account found for height update")
      return
    }

    val currentHeightInput =
      HeightInput.fromStoredHeight(
        storedHeight = currentAccount.height ?: 1700,
        isMetric = currentAccount.weightUnit.value == "kg",
      )

    dialogQueueService.enqueue(
      DialogModel.Custom(
        contentKey = DialogType.HeightPicker,
        params = mapOf("value" to currentHeightInput, "confirmText" to RadioGroupModalStrings.Button.Save),
        onConfirm = { selectedHeight ->
          if (selectedHeight is HeightInput) {
            onHeightUpdate(scope, stateProvider, selectedHeight)
          }
        },
        onDismiss = {
          dialogQueueService.dismissCurrent()
        },
        dismissOnBackPress = true,
      ),
    )
  }

  private fun onHeightUpdate(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
    heightInput: HeightInput,
  ) {
    val currentAccount = stateProvider().account
    if (currentAccount == null) {
      AppLog.e(TAG, "No active account found for height update")
      return
    }

    val newStoredHeight = heightInput.toStoredHeight()
    if (currentAccount.height == newStoredHeight) {
      AppLog.d(TAG, "Height is already set to $newStoredHeight, no update needed")
      return
    }

    dialogQueueService.showLoader("Updating height...")
    scope.launch {
      try {
        val bodyComposition =
          BodyCompUpdateRequest(
            height = newStoredHeight,
            activityLevel = currentAccount.activityLevel ?: "normal",
            weightUnit = currentAccount.weightUnit.value,
          )
        val updatedProfile = currentAccount.toGGBTUserProfile().copy(
          height = ConversionTools.convertStoredHeightToCm(newStoredHeight).toDouble(),
        )
        val scaleResult = scaleSettingsManager.updateR4Profile(updatedProfile)
        AppLog.d(TAG, "Scale result: $scaleResult")
        scaleSettingsManager.handleScaleUpdateResult(scaleResult)

        bodyCompositionService.updateBodyComposition(BodyCompUpdateType.HEIGHT, bodyComposition)
        AppLog.i(TAG, "Successfully updated height to ${heightInput.getString()}")
      } catch (e: Exception) {
        AppLog.e(TAG, "Error updating height", e)
      } finally {
        dialogQueueService.dismissLoader()
      }
    }
  }

  private fun onWeightlessUpdate(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
    isWeightlessOn: Boolean,
  ) {
    val currentAccount = stateProvider().account
    if (currentAccount?.isWeightlessOn == isWeightlessOn) {
      AppLog.d(TAG, "Weightless mode is already set to $isWeightlessOn, no update needed")
      return
    }

    dialogQueueService.showLoader("Updating weightless mode...")
    scope.launch {
      try {
        userSettingsService.toggleWeightlessSetting(
          isWeightlessOn = isWeightlessOn,
          weightlessWeight = if (isWeightlessOn) currentAccount?.weightlessWeight?.toDouble() else null,
        )
        AppLog.i(TAG, "Successfully updated weightless mode")
      } catch (e: Exception) {
        AppLog.e(TAG, "Error updating weightless mode", e)
      } finally {
        dialogQueueService.dismissLoader()
      }
    }
  }

}
