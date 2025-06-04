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
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                appViewModel.enqueueAlert(
                    title = "High Priority Alert",
                    message = "This is a high priority alert that will show immediately",
                    dismissText = "OK",
                    onDismiss = {},
                    priority = 1,  // Lower number means higher priority
                    delayMillis = 0L,  // No delay
                )
            },
        ) {
            Text("Show High Priority Alert")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                appViewModel.enqueueAlert(
                    title = "Medium Priority Alert",
                    message = "This is a medium priority alert with 2 second delay",
                    dismissText = "OK",
                    onDismiss = {},
                    priority = 50,  // Medium priority
                    delayMillis = 2000L,  // 2 second delay
                )
            },
        ) {
            Text("Show Medium Priority Alert")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                appViewModel.enqueueAlert(
                    title = "Low Priority Alert",
                    message = "This is a low priority alert with 5 second delay",
                    dismissText = "OK",
                    onDismiss = {},
                    priority = 100,  // Lower priority
                    delayMillis = 5000L,  // 5 second delay
                )
            },
        ) {
            Text("Show Low Priority Alert")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                // Enqueue multiple dialogs with different priorities
                appViewModel.enqueueAlert(
                    title = "Low Priority Alert",
                    message = "This should show last",
                    dismissText = "OK",
                    onDismiss = {},
                    priority = 100,
                    delayMillis = 1000L,
                )
                appViewModel.enqueueAlert(
                    title = "Medium Priority Alert",
                    message = "This should show second",
                    dismissText = "OK",
                    onDismiss = {},
                    priority = 50,
                    delayMillis = 1000L,
                )
                appViewModel.enqueueCustomDialog(
                    contentKey = "custom_dialog",
                    onDismiss = {},
                    priority = 1,
                    delayMillis = 1000L
                )

            },
        ) {
            Text("Enqueue Multiple Alerts")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                // Clear all dialogs
                appViewModel.clear()
            },
        ) {
            Text("Clear All Dialogs")
        }
        Spacer(modifier = Modifier.height(16.dp))

        Spacer(modifier = Modifier.height(24.dp))
        Text("Accounts:", style = MeAppTheme.typography.body1, color = MeAppTheme.colorScheme.body)
        accounts.forEach { (accountId, account) ->
            Column(
                modifier = Modifier
                    .background(MeAppTheme.colorScheme.secondary)
                    .padding(8.dp),
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


