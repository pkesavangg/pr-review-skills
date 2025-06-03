package com.greatergoods.meapp.features.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.features.common.viewmodel.AppViewModel
import com.greatergoods.meapp.theme.MeAppTheme

/**
 * Sample screen to manage user accounts and their theme modes using AppViewModel.
 * - Button to generate a random account and save to DataStore
 * - List of all accounts with their theme and actions to change theme or set active
 */
@Composable
fun SampleThemeScreen(
    appViewModel: AppViewModel
) {
    val accounts by appViewModel.accountsFlow.collectAsState()
    var showDialogForAccount by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeAppTheme.colorScheme.primary),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(onClick = { appViewModel.createRandomAccount() }) {
            Text("Generate Random Account")
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Accounts:", style = MeAppTheme.typography.body1, color = MeAppTheme.colorScheme.body)
        accounts.forEach { (accountId, account) ->
            Column(
                modifier = Modifier
                    .background(MeAppTheme.colorScheme.secondary)
                    .padding(8.dp)
            ) {
                Text("ID: $accountId", style = MeAppTheme.typography.body2)
                Text("Theme: ${account.themeMode}", style = MeAppTheme.typography.body2)
                Row {
                    Button(onClick = { showDialogForAccount = accountId }) {
                        Text("Change Theme")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { appViewModel.setActiveAccount(accountId) }) {
                        Text(if (account.isActive) "Active" else "Set Active")
                    }
                }
                if (showDialogForAccount == accountId) {
                    SampleThemeDialog(
                        selectedMode = account.themeMode,
                        onModeSelected = {
                            appViewModel.setThemeMode(accountId, it)
                            showDialogForAccount = null
                        },
                        onDismiss = { showDialogForAccount = null },
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}


