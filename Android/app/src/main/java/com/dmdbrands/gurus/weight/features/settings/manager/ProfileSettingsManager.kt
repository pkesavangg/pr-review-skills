package com.dmdbrands.gurus.weight.features.settings.manager

import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import com.dmdbrands.gurus.weight.domain.enums.ActivityLevel
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.api.user.BodyCompUpdateRequest
import com.dmdbrands.gurus.weight.domain.services.BodyCompUpdateType
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IBodyCompositionService
import com.dmdbrands.gurus.weight.domain.services.IUserSettingsService
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.common.components.RadioButtonOption
import com.dmdbrands.gurus.weight.features.common.components.showRadioGroupModal
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.settings.strings.RadioGroupModalStrings
import com.dmdbrands.gurus.weight.features.settings.viewmodel.SettingsIntent
import com.dmdbrands.gurus.weight.features.settings.viewmodel.SettingsState
import com.dmdbrands.gurus.weight.features.weightless.helper.WeightlessHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

interface IProfileSettingsManager {
  fun observeUserProfile(
    scope: CoroutineScope,
    dispatch: (SettingsIntent) -> Unit,
  )

  fun onActivityLevelClick(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
  )

  fun onGoalSettingClick(scope: CoroutineScope)

  fun onWeightlessClick(scope: CoroutineScope)

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

  override fun onActivityLevelClick(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
  ) {
    AppLog.d(TAG, "Activity level clicked")
    showActivityLevelModal(scope, stateProvider)
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
            height = currentAccount.height ?: BodyCompUpdateRequest.DEFAULT_HEIGHT,
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

}
