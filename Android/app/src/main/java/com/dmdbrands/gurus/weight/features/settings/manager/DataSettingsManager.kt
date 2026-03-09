package com.dmdbrands.gurus.weight.features.settings.manager

import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import com.dmdbrands.gurus.weight.domain.services.AuthState
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IExportService
import com.dmdbrands.gurus.weight.domain.services.IHealthConnectService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.RadioButtonOption
import com.dmdbrands.gurus.weight.features.common.components.showRadioGroupModal
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.settings.strings.RadioGroupModalStrings
import com.dmdbrands.gurus.weight.features.settings.strings.SettingsScreenStrings
import com.dmdbrands.gurus.weight.features.settings.viewmodel.SettingsIntent
import com.dmdbrands.gurus.weight.features.settings.viewmodel.SettingsState
import com.dmdbrands.gurus.weight.features.export.strings.ExportStrings
import retrofit2.HttpException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

interface IDataSettingsManager {
  fun observeExportEnabled(
    scope: CoroutineScope,
    dispatch: (SettingsIntent) -> Unit,
  )

  fun loadCurrentThemeMode(
    scope: CoroutineScope,
    dispatch: (SettingsIntent) -> Unit,
  )

  fun onExportDataClick(scope: CoroutineScope)

  fun onConfirmDeleteAccount(dispatch: (SettingsIntent) -> Unit)

  fun onDeleteAccount(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
  )

  fun onLogOutClick(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
    isLogoutAll: Boolean = false,
  )

  fun onAppearanceClick(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
    dispatch: (SettingsIntent) -> Unit,
  )
}

class DataSettingsManager
@Inject
constructor(
  private val entryService: IEntryService,
  private val accountService: IAccountService,
  private val exportService: IExportService,
  private val healthConnectService: IHealthConnectService,
  private val userDataStore: UserDataStore,
  private val dialogQueueService: IDialogQueueService,
  private val navigationService: IAppNavigationService,
) : IDataSettingsManager {
  companion object {
    private const val TAG = "DataSettingsManager"
  }

  override fun observeExportEnabled(
    scope: CoroutineScope,
    dispatch: (SettingsIntent) -> Unit,
  ) {
    scope.launch {
      try {
        entryService.latestEntry.collect { latestEntry ->
          val isEnabled = latestEntry != null
          dispatch(SettingsIntent.SetExportEnabled(isEnabled))
          AppLog.d(TAG, "Export enabled: $isEnabled (latestEntry: ${latestEntry != null})")
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error checking export enabled state", e.toString())
        dispatch(SettingsIntent.SetExportEnabled(false))
      }
    }
  }

  override fun loadCurrentThemeMode(
    scope: CoroutineScope,
    dispatch: (SettingsIntent) -> Unit,
  ) {
    scope.launch {
      userDataStore.currentThemeModeFlow.collect { themeMode ->
        val displayString = when (themeMode) {
          com.dmdbrands.gurus.weight.proto.ThemeMode.LIGHT -> RadioGroupModalStrings.Appearance.Light
          com.dmdbrands.gurus.weight.proto.ThemeMode.DARK -> RadioGroupModalStrings.Appearance.Dark
          else -> RadioGroupModalStrings.Appearance.System
        }
        dispatch(SettingsIntent.UpdateThemeMode(displayString))
      }
    }
  }

  override fun onExportDataClick(scope: CoroutineScope) {
    AppLog.d(TAG, "Export data clicked")

    dialogQueueService.enqueue(
      DialogModel.Confirm(
        title = ExportStrings.ExportDialogTitle,
        message = ExportStrings.ExportDialogMessage,
        confirmText = ExportStrings.SendButton,
        cancelText = ExportStrings.CancelButton,
        onConfirm = {
          performExport(scope)
          dialogQueueService.dismissCurrent()
        },
        onCancel = {
          AppLog.d(TAG, "User cancelled export")
          dialogQueueService.dismissCurrent()
        },
      ),
    )
  }

  override fun onConfirmDeleteAccount(dispatch: (SettingsIntent) -> Unit) {
    dialogQueueService.enqueue(
      DialogModel.Confirm(
        title = SettingsScreenStrings.DeleteAccountDialog.Title,
        message = SettingsScreenStrings.DeleteAccountDialog.Body,
        primaryActionType = ButtonType.ErrorText,
        confirmText = SettingsScreenStrings.DeleteAccountDialog.Confirm,
        cancelText = SettingsScreenStrings.DeleteAccountDialog.Cancel,
        onConfirm = { dispatch(SettingsIntent.DeleteAccount) },
        onCancel = {},
      ),
    )
  }

  override fun onDeleteAccount(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
  ) {
    dialogQueueService.showLoader(SettingsScreenStrings.DeletingAccount)
    scope.launch {
      try {
        val account = stateProvider().account
        if (account != null) {
          accountService.deleteAccount(account.id, account.isActiveAccount)
          healthConnectService.clearHealthConnect()
          navigationService.emitAuthEvent(AuthState.AccountDeleted(account.isActiveAccount))
        }
        dialogQueueService.dismissLoader()
      } catch (e: Exception) {
        dialogQueueService.dismissLoader()
      } finally {
        dialogQueueService.clear()
      }
    }
  }

  override fun onLogOutClick(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
    isLogoutAll: Boolean,
  ) {
    val logoutModalString = SettingsScreenStrings.LogoutDialog
    val title =
      if (isLogoutAll) SettingsScreenStrings.LogoutDialog.LogoutAll.Title else SettingsScreenStrings.LogoutDialog.Logout.Title
    val body =
      if (isLogoutAll) SettingsScreenStrings.LogoutDialog.LogoutAll.Body else SettingsScreenStrings.LogoutDialog.Logout.Body

    dialogQueueService.enqueue(
      DialogModel.Confirm(
        title,
        body,
        primaryActionType = ButtonType.ErrorText,
        logoutModalString.Confirm,
        logoutModalString.Cancel,
        onDismiss = {},
        onConfirm = {
          if (isLogoutAll) {
            onLogoutAllAccounts(scope)
          } else {
            logout(scope, stateProvider)
          }
        },
      ),
    )
  }

  override fun onAppearanceClick(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
    dispatch: (SettingsIntent) -> Unit,
  ) {
    AppLog.d(TAG, "Appearance clicked")
    showAppearanceModal(scope, stateProvider, dispatch)
  }

  private fun showAppearanceModal(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
    dispatch: (SettingsIntent) -> Unit,
  ) {
    showRadioGroupModal(
      dialogService = dialogQueueService,
      title = RadioGroupModalStrings.Titles.Appearance,
      options = listOf(
        RadioButtonOption("LIGHT", RadioGroupModalStrings.Appearance.Light),
        RadioButtonOption("DARK", RadioGroupModalStrings.Appearance.Dark),
        RadioButtonOption("SYSTEM", RadioGroupModalStrings.Appearance.System),
      ),
      selectedItem = getCurrentThemeModeString(stateProvider),
      confirmText = RadioGroupModalStrings.Button.Save,
      onConfirm = { selectedTheme ->
        selectedTheme?.let { themeValue ->
          onAppearanceUpdate(scope, stateProvider, dispatch, themeValue.toString())
        }
      },
      onCancel = {
        AppLog.d(TAG, "Appearance selection cancelled")
      },
    )
  }

  private fun getCurrentThemeModeString(stateProvider: () -> SettingsState): String {
    return when (stateProvider().currentThemeMode) {
      RadioGroupModalStrings.Appearance.Light -> "LIGHT"
      RadioGroupModalStrings.Appearance.Dark -> "DARK"
      else -> "SYSTEM"
    }
  }

  private fun onAppearanceUpdate(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
    dispatch: (SettingsIntent) -> Unit,
    themeModeString: String,
  ) {
    val currentAccount = stateProvider().account
    if (currentAccount == null) {
      AppLog.e(TAG, "No active account found for appearance update")
      return
    }

    val themeMode = when (themeModeString) {
      "LIGHT" -> com.dmdbrands.gurus.weight.proto.ThemeMode.LIGHT
      "DARK" -> com.dmdbrands.gurus.weight.proto.ThemeMode.DARK
      else -> com.dmdbrands.gurus.weight.proto.ThemeMode.SYSTEM
    }

    val displayString = when (themeModeString) {
      "LIGHT" -> RadioGroupModalStrings.Appearance.Light
      "DARK" -> RadioGroupModalStrings.Appearance.Dark
      else -> RadioGroupModalStrings.Appearance.System
    }

    dialogQueueService.showLoader("Updating appearance...")
    scope.launch {
      try {
        userDataStore.setThemeMode(currentAccount.id, themeMode)
        dispatch(SettingsIntent.UpdateThemeMode(displayString))
        AppLog.i(TAG, "Successfully updated appearance to $displayString")
      } catch (e: Exception) {
        AppLog.e(TAG, "Error updating appearance", e)
      } finally {
        dialogQueueService.dismissLoader()
      }
    }
  }

  private fun performExport(scope: CoroutineScope) {
    AppLog.i(TAG, ExportStrings.ExportStarted)
    dialogQueueService.showLoader(
      message = ExportStrings.LoaderMessage,
    )

    scope.launch {
      try {
        exportService.exportCsvWithPrompt()
        AppLog.i(TAG, ExportStrings.ExportCompleted)
      } catch (e: HttpException) {
        AppLog.e(TAG, ExportStrings.ExportFailed, e)
      } finally {
        dialogQueueService.dismissLoader()
      }
    }
  }

  private fun logout(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
  ) {
    dialogQueueService.showLoader(SettingsScreenStrings.LoggingOut)
    scope.launch {
      try {
        val account = stateProvider().account
        if (account != null) {
          accountService.logout(account.id, account.fcmToken)
          navigationService.reInitialize()
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to log out", e)
      } finally {
        dialogQueueService.dismissLoader()
        dialogQueueService.clear()
      }
    }
  }

  private fun onLogoutAllAccounts(scope: CoroutineScope) {
    dialogQueueService.showLoader(SettingsScreenStrings.LoggingOutAll)
    scope.launch {
      try {
        accountService.logoutAll()
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to log out all accounts", e)
      } finally {
        dialogQueueService.dismissLoader()
        dialogQueueService.clear()
      }
    }
  }
}
