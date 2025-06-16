package com.greatergoods.meapp.features.auth

import androidx.compose.foundation.background
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
import com.greatergoods.meapp.features.sample.HomeViewModel
import com.greatergoods.meapp.proto.ThemeMode
import com.greatergoods.meapp.proto.UserAccount
import com.greatergoods.meapp.theme.MeAppTheme

/**
 * Screen displaying a list of user accounts, allowing the user to select an account to activate.
 */
@Composable
fun UserListScreen() {
    val viewModel : HomeViewModel = hiltViewModel()
    val accounts by viewModel.allAccounts.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Select an Account",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            if (accounts.isEmpty()) {
                Text(
                    text = "No accounts available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(accounts.entries.toList()) { (accountId, account) ->
                        AccountListItem(
                            accountId = accountId,
                            account = account,
                            isActive = account.isActive,
                            onClick = { viewModel.switchAccount(accountId) },
                        )
                    }
                }
            }
            Button(onClick = { viewModel.createRandomAccount() }) {
                Text("Generate Random Account")
            }

        }
    }
}

/**
 * Displays a single account item in the user list.
 *
 * @param accountId The unique ID of the account.
 * @param account The [UserAccount] data.
 * @param isActive Whether this account is currently active.
 * @param onClick Callback when the item is clicked.
 */
@Composable
private fun AccountListItem(
    accountId: String,
    account: UserAccount,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(
                if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surface,
            ),
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Account ID: $accountId",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Theme: ${account.themeMode.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isActive) {
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

/**
 * Preview of [UserListScreen] in light mode with sample data.
 */
@PreviewTheme
@Composable
private fun PreviewUserListScreen_Light() {
    MeAppTheme(themeMode = ThemeMode.LIGHT) {
        UserListScreenPreviewContent()
    }
}

/**
 * Preview of [UserListScreen] in dark mode with sample data.
 */
@PreviewTheme
@Composable
private fun PreviewUserListScreen_Dark() {
    MeAppTheme(themeMode = ThemeMode.DARK) {
        UserListScreenPreviewContent()
    }
}

@Composable
private fun UserListScreenPreviewContent() {
    val sampleAccounts = mapOf(
        "user1" to UserAccount.newBuilder().setIsActive(true).setThemeMode(ThemeMode.LIGHT).build(),
        "user2" to UserAccount.newBuilder().setIsActive(false).setThemeMode(ThemeMode.DARK).build(),
        "user3" to UserAccount.newBuilder().setIsActive(false).setThemeMode(ThemeMode.SYSTEM).build(),
    )
    Surface(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Select an Account",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(sampleAccounts.entries.toList()) { (accountId, account) ->
                    AccountListItem(
                        accountId = accountId,
                        account = account,
                        isActive = account.isActive,
                        onClick = {},
                    )
                }
            }
        }
    }
}
