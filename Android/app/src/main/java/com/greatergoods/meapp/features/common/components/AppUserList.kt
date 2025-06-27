package com.greatergoods.meapp.features.common.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.domain.model.storage.Account.Account
import com.greatergoods.meapp.resources.AppIcons
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
    modifier: Modifier = Modifier,
    showAccountActivity: Boolean = false,
    onDeleteRequest: (Account) -> Unit,
    onAccountSelect: (Account) -> Unit,
    onLoginRequest: (Account) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    footerContent: @Composable (() -> Unit)? = null,
) {
    AppDraggableList(
        items = accounts,
        iconWidth = 56.dp,
        contentPadding = contentPadding,
        keySelector = { it.id },
        trailingActions = { index, item ->
            val targetCornerRadius = MeTheme.borderRadius.sm
            val shape =
                when {
                    accounts.size == 1 -> RoundedCornerShape(targetCornerRadius + 2.dp)
                    index == 0 ->
                        RoundedCornerShape(
                            topEnd = targetCornerRadius + 2.dp,
                        )

                    index == accounts.size - 1 ->
                        RoundedCornerShape(
                            bottomEnd = targetCornerRadius + 2.dp,
                        )

                    else -> RectangleShape
                }
            AppDraggableListActions(
                shape = shape,
            ) {
                AppDraggableActionItem(
                    iconId = AppIcons.Default.Delete,
                    text = "Delete",
                    contentDescription = "Delete item",
                    backgroundColor = MeTheme.colorScheme.danger,
                ) {
                    onDeleteRequest(item)
                }
            }
        },
        footerContent = footerContent,
    ) { item, progress ->
        val isDragging = progress > 0f
        val targetCornerRadius = if (isDragging) 0.dp else MeTheme.borderRadius.sm
        val animatedCornerRadius by animateDpAsState(
            targetValue = targetCornerRadius,
            animationSpec = tween(durationMillis = 250),
        )
        val index = accounts.indexOf(item)
        val shape =
            when {
                accounts.size == 1 -> RoundedCornerShape(animatedCornerRadius)
                index == 0 ->
                    RoundedCornerShape(
                        topStart = animatedCornerRadius,
                        topEnd = animatedCornerRadius,
                    )

                index == accounts.size - 1 ->
                    RoundedCornerShape(
                        bottomStart = animatedCornerRadius,
                        bottomEnd = animatedCornerRadius,
                    )

                else -> RectangleShape
            }

        Column {
            AppUser(
                account = item,
                modifier = Modifier.clip(shape),
                onAccountSelect = { onAccountSelect(item) },
                onLoginRequest = { onLoginRequest(item) },
                avatarAlpha = 1f - progress,
                showAccountActivity = showAccountActivity,
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

@PreviewTheme
@Composable
fun AppUserListPreview() {
    MeAppTheme {
        val sampleAccounts =
            remember {
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
                        isSynced = true,
                        isExpired = false,
                        weightUnit = "lbs",
                        isWeightlessOn = true,
                        height = 175,
                        activityLevel = "Moderately Active",
                        weightlessTimestamp = "2024-01-15T10:30:00.000Z",
                        weightlessWeight = 70.5f,
                        isStreakOn = true,
                        dashboardType = "Dashboard4",
                        dashboardMetrics = listOf("weight", "bmi"),
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
                        isSynced = true,
                        isExpired = false,
                        weightUnit = "kg",
                        isWeightlessOn = false,
                        height = 165,
                        activityLevel = "Active",
                        weightlessTimestamp = null,
                        weightlessWeight = null,
                        isStreakOn = false,
                        dashboardType = "Dashboard12",
                        dashboardMetrics = listOf("weight", "bodyfat", "muscle"),
                    ),
                )
            }

        AppScaffold(
            title = "User Accounts",
            containerColor = MeTheme.colorScheme.secondaryBackground,
        ) { modifier ->
            AppUserList(
                modifier = modifier,
                accounts = sampleAccounts,
                onDeleteRequest = {},
                onAccountSelect = {},
                onLoginRequest = {},
            )
        }
    }
}

@PreviewTheme
@Composable
fun AppUserListSingleItemPreview() {
    MeAppTheme {
        val singleAccount =
            remember {
                listOf(
                    Account(
                        id = "1",
                        firstName = "Single",
                        lastName = "User",
                        dob = "1990-01-01",
                        email = "single.user@example.com",
                        gender = "Male",
                        isActiveAccount = true,
                        isLoggedIn = true,
                        lastActiveTime = "2024-01-15T10:30:00.000Z",
                        zipcode = "12345",
                        isSynced = true,
                        isExpired = false,
                        weightUnit = "lbs",
                        isWeightlessOn = false,
                        height = 180,
                        activityLevel = "Very Active",
                        weightlessTimestamp = null,
                        weightlessWeight = null,
                        isStreakOn = true,
                        dashboardType = "Dashboard4",
                        dashboardMetrics = listOf("weight"),
                    ),
                )
            }

        AppScaffold(
            title = "Single User",
            containerColor = MeTheme.colorScheme.secondaryBackground,
        ) { modifier ->
            AppUserList(
                modifier = modifier,
                accounts = singleAccount,
                onDeleteRequest = {},
                onAccountSelect = {},
                onLoginRequest = {},
            )
        }
    }
}
