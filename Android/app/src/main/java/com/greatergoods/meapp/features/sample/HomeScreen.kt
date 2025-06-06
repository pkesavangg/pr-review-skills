package com.greatergoods.meapp.features.sample

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.proto.ThemeMode
import com.greatergoods.meapp.proto.UserAccount
import com.greatergoods.meapp.theme.MeAppTheme
import android.annotation.SuppressLint

/**
 * Home screen displaying current user data, logout option, and switch account section.
 *
 * @param selectedRoute The currently selected top-level route.
 * @param onSelect Callback to select a top-level route.
 * @param content The content to display above the bottom navigation.
 */
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun HomeScreen(
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val currentAccount by viewModel.currentAccount.collectAsStateWithLifecycle()
    val allAccounts by viewModel.allAccounts.collectAsStateWithLifecycle()
    // Debug: Log the active account

    Scaffold { innerPadding ->
        Surface(modifier = Modifier.padding(innerPadding)) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // Current user info
                Text(
                    text = "Current User",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (currentAccount != null) {
                    AccountInfoCard(account = currentAccount!!)
                    Button(
                        onClick = { viewModel.logout() },
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text("Log Out")
                    }
                } else {
                    Text(
                        text = "No active user.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                // Switch account section
                Text(
                    text = "Switch Account",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                val currentId = currentAccount?.refreshToken ?: ""
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(allAccounts.entries.filter { it.key != currentId }) { (accountId, account) ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.switchAccount(accountId) },
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = account.refreshToken,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Normal,
                                    )
                                    Text(
                                        text = "ID: $accountId",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = "Theme: ${account.themeMode.name}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (account.isActive) {
                                    Text(
                                        text = "Active",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Card displaying account information.
 *
 * @param account The user account to display.
 */
@Composable
private fun AccountInfoCard(account: UserAccount) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = account.refreshToken,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Theme: ${account.themeMode.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (account.accessToken.isNotEmpty()) {
                Text(
                    text = "Access: ${account.accessToken}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * Preview of [HomeScreen] in light mode with sample data.
 */
@PreviewTheme
@Composable
private fun PreviewHomeScreen_Light() {
    MeAppTheme {
        HomeScreenPreviewContent()
    }
}

/**
 * Preview of [HomeScreen] in dark mode with sample data.
 */
@PreviewTheme
@Composable
private fun PreviewHomeScreen_Dark() {
    MeAppTheme {
        HomeScreenPreviewContent()
    }
}

@Composable
private fun HomeScreenPreviewContent() {
    val sampleCurrent = UserAccount.newBuilder()
        .setIsActive(true)
        .setThemeMode(ThemeMode.LIGHT)
        .setRefreshToken("user1@example.com")
        .setAccessToken("token1")
        .build()
    val sampleAccounts = mapOf(
        "user1" to sampleCurrent,
        "user2" to UserAccount.newBuilder().setIsActive(false).setThemeMode(ThemeMode.DARK)
            .setRefreshToken("user2@example.com").setAccessToken("token2").build(),
        "user3" to UserAccount.newBuilder().setIsActive(false).setThemeMode(ThemeMode.SYSTEM)
            .setRefreshToken("user3@example.com").setAccessToken("token3").build(),
    )
    Surface {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Current User",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            AccountInfoCard(account = sampleCurrent)
            Button(
                onClick = {},
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("Log Out")
            }
            Text(
                text = "Switch Account",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 24.dp),
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(sampleAccounts.entries.filter { it.key != "user1" }) { (accountId, account) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { },
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = account.refreshToken,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Normal,
                                )
                                Text(
                                    text = "ID: $accountId",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "Theme: ${account.themeMode.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (account.isActive) {
                                Text(
                                    text = "Active",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
