package com.dmdbrands.gurus.weight.features.MyAccounts.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.features.MyAccounts.reducer.MyAccountsIntent
import com.dmdbrands.gurus.weight.features.MyAccounts.reducer.MyAccountsState
import com.dmdbrands.gurus.weight.features.MyAccounts.strings.MyAccountsScreenStrings
import com.dmdbrands.gurus.weight.features.MyAccounts.viewmodel.MyAccountsViewModel
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.AppUserList
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme
import kotlinx.coroutines.launch

/**
 * MyAccountsScreen displays the list of logged-in accounts and allows switching, login, and removal.
 * Shows a max accounts dialog if the limit is reached.
 */
@Composable
fun MyAccountsScreen() {
  val viewmodel: MyAccountsViewModel = hiltViewModel()
  val state by viewmodel.state.collectAsState()
  rememberCoroutineScope()
  MyAccountsScreenContent(
    state = state,
    handleIntent = viewmodel::handleIntent,
    onNavigateBack = viewmodel::onNavigateBack,
  )
}

@Composable
fun MyAccountsScreenContent(
  state: MyAccountsState,
  handleIntent: (MyAccountsIntent) -> Unit,
  onNavigateBack: () -> Unit,
) {
  val backStack = LocalNavBackStack.current
  val coroutineScope = rememberCoroutineScope()

  // Handle system back button
  BackHandler {
    onNavigateBack() // Start scanning when navigating back
    coroutineScope.launch {
      backStack.removeLast()
    }
  }
  AppScaffold(
    title = MyAccountsScreenStrings.Title,
    navigationIcon = {
      AppIconButton(AppIcons.Default.Close) {
        onNavigateBack() // Start scanning when navigating back
        coroutineScope.launch {
          backStack.removeLast()
        }
      }
    },
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxSize(),
      verticalArrangement = Arrangement.Top,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      AppUserList(
        accounts = state.accounts,
        modifier = Modifier.background(Color.Green),
        showAccountActivity = true,
        canRemoveAccount = true,
        onDeleteRequest = { handleIntent(MyAccountsIntent.RequestRemoveAccount(it)) },
        onAccountSelect = { handleIntent(MyAccountsIntent.SelectAccount(it)) },
        onLoginRequest = { handleIntent(MyAccountsIntent.LoginToAccount(it)) },
        contentPadding = PaddingValues(horizontal = MeTheme.spacing.sm, vertical = MeTheme.spacing.md),
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
          Spacer(modifier = Modifier.height(MeTheme.spacing.x3l))
          AppButton(
            label = MyAccountsScreenStrings.LogIntoExistingAccount,
            type = ButtonType.PrimaryOutlined,
            onClick = { handleIntent(MyAccountsIntent.LoginToAccount()) },
          )
          Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
          AppButton(
            label = MyAccountsScreenStrings.CreateNewAccount,
            type = ButtonType.TextPrimary,
            onClick = { handleIntent(MyAccountsIntent.CreateAccount) },
          )
        }
      }
    }
  }
}
