package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.dmdbrands.gurus.weight.core.shared.utilities.testing.TestTags
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.features.common.strings.AppListStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * A list component that displays user accounts with swipe-to-delete functionality.
 *
 * @param accounts List of accounts to display.
 * @param maxVisibleItems Maximum number of items to display in the viewport. If null, all items are displayed.
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
    canRemoveAccount: Boolean = false,
    maxVisibleItems: Int? = null,
    onDeleteRequest: (Account) -> Unit,
    onAccountSelect: (Account) -> Unit,
    onLoginRequest: (Account) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    footerContent: @Composable (() -> Unit)? = null,
) {
    AppSwipeableList(
        items = accounts,
        iconWidth = 56.dp,
        maxVisibleItems = maxVisibleItems,
        isItemSwipeable = { canRemoveAccount },
        contentPadding = contentPadding,
        keySelector = { it.id },
        trailingActions = { index, item ->
            val targetCornerRadius = MeTheme.borderRadius.sm
            val shape =
                when {
                    accounts.size == 1 -> RoundedCornerShape(
                        topEnd = targetCornerRadius + 2.dp,
                        bottomEnd = targetCornerRadius + 2.dp,
                    )
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
            AppSwipeableListActions (
                shape = shape,
            ) {
                AppSwipeableActionItem(
                    iconId = AppIcons.Default.Delete,
                    contentDescription = AppListStrings.accDeleteItemLabel,
                    backgroundColor = MeTheme.colorScheme.danger,
                    modifier = Modifier.testTag("${TestTags.Landing.AccountCardDeleteButton}_${item.id}"),
                ) {
                    onDeleteRequest(item)
                }
            }
        },
        footerContent = footerContent,
    ) { item ->
        Swipeable { progress ->
            val isDragging = progress > 0f
          key(isDragging) {
            val targetCornerRadius = if (isDragging) 0.dp else MeTheme.borderRadius.sm
            val index = accounts.indexOf(item)
            val shape =
              when {
                accounts.size == 1 -> RoundedCornerShape(targetCornerRadius)
                index == 0 ->
                  RoundedCornerShape(
                    topStart = targetCornerRadius,
                    topEnd = targetCornerRadius,
                  )

                index == accounts.size - 1 ->
                  RoundedCornerShape(
                    bottomStart = targetCornerRadius,
                    bottomEnd = targetCornerRadius,
                  )

                else -> RectangleShape
              }
            Column(
              modifier = Modifier
                .clip(shape)
                .background(MeTheme.colorScheme.primaryBackground, shape),
            ) {
              // TalkBack: swipe-to-delete is a gesture a screen-reader user can't perform,
              // so expose the same delete as a custom action. It is attached to AppUser's
              // own (clickable, merged) row so the action shares the row's focusable node.
              val deleteActionModifier = if (canRemoveAccount) {
                Modifier.semantics {
                  customActions = listOf(
                    CustomAccessibilityAction(AppListStrings.accDeleteItemLabel) {
                      onDeleteRequest(item)
                      true
                    },
                  )
                }
              } else {
                Modifier
              }
              AppUser(
                account = item,
                modifier = Modifier
                  .clip(shape)
                  .testTag("${TestTags.Landing.AccountCardRow}_${item.id}")
                  .then(deleteActionModifier),
                onAccountSelect = { onAccountSelect(item) },
                onLoginRequest = { onLoginRequest(item) },
                avatarAlpha = 1f - progress,
                showAccountActivity = showAccountActivity,
              )
              if (accounts.size > 1 && accounts.indexOf(item) < accounts.size - 1) {
                HorizontalDivider(
                  color = MeTheme.colorScheme.utility,
                  thickness = .5.dp,
                )
              }
            }
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
                        weightUnit = WeightUnit.LB,
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
                        weightUnit = WeightUnit.KG,
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
                maxVisibleItems = null,
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
                        weightUnit = WeightUnit.LB,
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
                maxVisibleItems = null,
                onDeleteRequest = {},
                onAccountSelect = {},
                onLoginRequest = {},
            )
        }
    }
}

@PreviewTheme
@Composable
fun AppUserListMaxVisibleItemsPreview() {
    MeAppTheme {
        val manyAccounts = remember {
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
                    weightUnit = WeightUnit.LB,
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
                    weightUnit = WeightUnit.KG,
                    isWeightlessOn = false,
                    height = 165,
                    activityLevel = "Active",
                    weightlessTimestamp = null,
                    weightlessWeight = null,
                    isStreakOn = false,
                    dashboardType = "Dashboard12",
                    dashboardMetrics = listOf("weight", "bodyfat", "muscle"),
                ),
                Account(
                    id = "3",
                    firstName = "Bob",
                    lastName = "Johnson",
                    dob = "1988-03-20",
                    email = "bob.johnson@example.com",
                    gender = "Male",
                    isActiveAccount = false,
                    isLoggedIn = true,
                    lastActiveTime = "2024-01-12T09:15:00.000Z",
                    zipcode = "54321",
                    isSynced = true,
                    isExpired = false,
                    weightUnit = WeightUnit.LB,
                    isWeightlessOn = false,
                    height = 180,
                    activityLevel = "Very Active",
                    weightlessTimestamp = "2024-01-12T09:15:00.000Z",
                    weightlessWeight = 75.2f,
                    isStreakOn = true,
                    dashboardType = "Dashboard4",
                    dashboardMetrics = listOf("weight", "bmi", "muscle"),
                ),
                Account(
                    id = "4",
                    firstName = "Alice",
                    lastName = "Brown",
                    dob = "1992-07-10",
                    email = "alice.brown@example.com",
                    gender = "Female",
                    isActiveAccount = false,
                    isLoggedIn = true,
                    lastActiveTime = "2024-01-14T16:45:00.000Z",
                    zipcode = "98765",
                    isSynced = true,
                    isExpired = false,
                    weightUnit = WeightUnit.LB,
                    isWeightlessOn = false,
                height = 160,
                activityLevel = "Moderately Active",
                weightlessTimestamp = null,
                weightlessWeight = null,
                isStreakOn = false,
                dashboardType = "Dashboard12",
                dashboardMetrics = listOf("weight", "bodyfat"),
                ),
                Account(
                    id = "5",
                    firstName = "Charlie",
                    lastName = "Wilson",
                    dob = "1987-11-05",
                    email = "charlie.wilson@example.com",
                    gender = "Male",
                    isActiveAccount = false,
                    isLoggedIn = true,
                    lastActiveTime = "2024-01-13T11:30:00.000Z",
                    zipcode = "13579",
                    isSynced = true,
                    isExpired = false,
                    weightUnit = WeightUnit.LB,
                    isWeightlessOn = true,
                height = 175,
                activityLevel = "Active",
                weightlessTimestamp = "2024-01-13T11:30:00.000Z",
                weightlessWeight = 68.9f,
                isStreakOn = true,
                dashboardType = "Dashboard4",
                dashboardMetrics = listOf("weight", "bmi", "muscle", "bodyfat"),
                ),
                Account(
                    id = "6",
                    firstName = "Diana",
                    lastName = "Davis",
                    dob = "1995-04-25",
                    email = "diana.davis@example.com",
                    gender = "Female",
                    isActiveAccount = false,
                    isLoggedIn = true,
                    lastActiveTime = "2024-01-11T13:20:00.000Z",
                    zipcode = "24680",
                    isSynced = true,
                    isExpired = false,
                    weightUnit = WeightUnit.LB,
                    isWeightlessOn = false,
                height = 165,
                activityLevel = "Very Active",
                weightlessTimestamp = null,
                weightlessWeight = null,
                isStreakOn = false,
                dashboardType = "Dashboard12",
                dashboardMetrics = listOf("weight", "muscle"),
                ),
                Account(
                    id = "7",
                    firstName = "Edward",
                    lastName = "Miller",
                    dob = "1983-09-15",
                    email = "edward.miller@example.com",
                    gender = "Male",
                    isActiveAccount = false,
                    isLoggedIn = true,
                    lastActiveTime = "2024-01-16T08:45:00.000Z",
                    zipcode = "11223",
                    isSynced = true,
                    isExpired = false,
                    weightUnit = WeightUnit.LB,
                    isWeightlessOn = true,
                height = 185,
                activityLevel = "Moderately Active",
                weightlessTimestamp = "2024-01-16T08:45:00.000Z",
                weightlessWeight = 82.1f,
                isStreakOn = true,
                dashboardType = "Dashboard4",
                dashboardMetrics = listOf("weight", "bmi"),
                ),
            )
        }

        AppScaffold(
            title = "Max 5 Visible Items",
            containerColor = MeTheme.colorScheme.secondaryBackground,
        ) { modifier ->
            AppUserList(
                modifier = modifier,
                accounts = manyAccounts,
                maxVisibleItems = 5,
                onDeleteRequest = {},
                onAccountSelect = {},
                onLoginRequest = {},
            )
        }
    }
}
