package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.domain.model.Account
import com.greatergoods.meapp.features.common.helper.rememberAppDraggableListState
import com.greatergoods.meapp.features.common.model.ActionButton
import com.greatergoods.meapp.features.common.strings.AppPopupStrings
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

/**
 * A list component that displays user accounts with swipe-to-delete functionality.
 *
 * @param accounts List of accounts to display.
 * @param onDeleteRequest Callback when a user requests to delete an account.
 * @param onAccountSelect Callback when an account is selected.
 * @param onLoginRequest Callback when a user requests to log in.
 * @param modifier Modifier for styling.
 * @param contentPadding Padding for the list content.
 */
@Composable
fun AppUserList(
    accounts: List<Account>,
    onDeleteRequest: (Account) -> Unit,
    onAccountSelect: (Account) -> Unit,
    onLoginRequest: (Account) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = MeTheme.spacing.md),
) {
    val draggableListState = rememberAppDraggableListState()

    if (accounts.isNotEmpty()) {
        AppDraggableList(
            items = accounts,
            draggableListState = draggableListState,
            modifier = modifier,
            iconWidth = 56.dp,
            contentPadding = contentPadding,
            onDelete = onDeleteRequest,
            isItemDraggable = { account -> !account.isActiveAccount && account.isLoggedIn },
        ) { item, progress ->
            val shape = when {
                accounts.size == 1 -> RoundedCornerShape(MeTheme.borderRadius.lg)
                accounts.indexOf(item) == accounts.size - 1 -> RoundedCornerShape(
                    bottomStart = MeTheme.borderRadius.lg,
                    bottomEnd = MeTheme.borderRadius.lg,
                )

                else -> RectangleShape
            }

            Column {
                AppUser(
                    account = item,
                    onAccountSelect = { onAccountSelect(item) },
                    onLoginRequest = { onLoginRequest(item) },
                    avatarAlpha = 1f - progress,
                    shape = shape,
                )
                if (accounts.size > 1 && accounts.indexOf(item) < accounts.size - 1) {
                    HorizontalDivider(
                        color = MeTheme.colorScheme.utility,
                        thickness = 1.dp,
                    )
                }
            }
        }
    }
}

@PreviewTheme
@Composable
fun AppUserListPreview() {
    MeAppTheme {
        var showDeleteDialog by remember { mutableStateOf(false) }
        var accountToDelete by remember { mutableStateOf<Account?>(null) }
        val sampleAccounts = remember {
            mutableStateOf(
                listOf(
                    Account(
                        id = "1",
                        firstName = "John",
                        lastName = "Doe",
                        dob = "1990-01-01",
                        email = "john.doe@example.com",
                        gender = "Male",
                        isActiveAccount = true,
                        isLoggedIn = true,
                        lastActiveTime = "2024-01-15T10:30:00.000Z",
                        zipcode = "12345",
                    ),
                    Account(
                        id = "2",
                        firstName = "Jane",
                        lastName = "Smith",
                        dob = "1985-05-15",
                        email = "jane.smith@example.com",
                        gender = "Female",
                        isActiveAccount = false,
                        isLoggedIn = true,
                        lastActiveTime = "2024-01-10T14:20:00.000Z",
                        zipcode = "67890",
                    ),
                    Account(
                        id = "3",
                        firstName = "Mike",
                        lastName = "Johnson",
                        dob = "1992-12-03",
                        email = "mike.johnson@example.com",
                        gender = "Male",
                        isActiveAccount = false,
                        isLoggedIn = false,
                        lastActiveTime = "2024-01-12T09:15:00.000Z",
                        zipcode = "54321",
                    ),
                    Account(
                        id = "4",
                        firstName = "Hei",
                        lastName = "Hei",
                        dob = "1985-05-15",
                        email = "hei.hei@example.com",
                        gender = "Female",
                        isActiveAccount = false,
                        isLoggedIn = true,
                        lastActiveTime = "2024-01-10T14:20:00.000Z",
                        zipcode = "67890",
                    ),
                ),
            )
        }

        AppScaffold(
            title = "User Accounts",
            containerColor = MeTheme.colorScheme.secondaryBackground,
        ) { modifier ->
            AppUserList(
                modifier = modifier,
                accounts = sampleAccounts.value,
                onDeleteRequest = {
                    accountToDelete = it
                    showDeleteDialog = true
                },
                onAccountSelect = { selectedAccount ->
                    sampleAccounts.value = sampleAccounts.value.map {
                        it.copy(isActiveAccount = it.id == selectedAccount.id)
                    }
                },
                onLoginRequest = {},
            )
        }

        if (showDeleteDialog && accountToDelete != null) {
            val username = "${accountToDelete!!.firstName} ${accountToDelete!!.lastName}"
            AppDialog(
                title = AppPopupStrings.AppRemoveUserStrings.RemoveUserTitle.format(username),
                body = AppPopupStrings.AppRemoveUserStrings.RemoveUserMessage.format(username),
                confirmAction = ActionButton(
                    text = AppPopupStrings.AppRemoveUserStrings.RemoveButton,
                    action = {
                        sampleAccounts.value = sampleAccounts.value.filter { it.id != accountToDelete?.id }
                        showDeleteDialog = false
                        accountToDelete = null
                    },
                ),
                dismissAction = ActionButton(
                    text = AppPopupStrings.AppRemoveUserStrings.CancelButton,
                    action = {
                        showDeleteDialog = false
                        accountToDelete = null
                    },
                ),
            )
        }
    }
}
