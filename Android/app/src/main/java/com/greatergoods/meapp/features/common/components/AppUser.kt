package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.domain.model.storage.Account.Account
import com.greatergoods.meapp.features.common.strings.AppUserStrings
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme.colorScheme
import com.greatergoods.meapp.theme.MeTheme.spacing
import com.greatergoods.meapp.theme.MeTheme.typography

/**
 * A user account item that displays profile information and a selector, matching the Figma design.
 *
 * @param account The account data to display.
 * @param onAccountSelect Callback when the account is selected.
 * @param onLoginRequest Callback when the login button is clicked.
 * @param modifier Modifier for styling.
 * @param avatarAlpha The alpha value for the avatar.
 * @param shape The shape for the background and clip.
 */
@Composable
fun AppUser(
    account: Account,
    onAccountSelect: () -> Unit,
    onLoginRequest: () -> Unit,
    modifier: Modifier = Modifier,
    avatarAlpha: Float = 1f,
    shape: Shape = RectangleShape,
    showAccountActivity: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colorScheme.primaryBackground, shape = shape)
            .clip(shape)
            .clickable(
                enabled = account.isLoggedIn && !account.isExpired,
                onClick = onAccountSelect,
            )
            .padding(
                start = spacing.sm,
                top = spacing.sm,
                bottom = spacing.sm,
                end = if (account.isLoggedIn) spacing.sm else 0.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppProfileAvatar(
            text = account.firstName,
            isActive = account.isActiveAccount,
            enabled = account.isLoggedIn,
            modifier = Modifier.alpha(avatarAlpha),
        )

        Spacer(modifier = Modifier.width(spacing.md))

        // User Info (Name and Email)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.firstName,
                style = typography.body2.copy(
                    color = colorScheme.textHeading,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(spacing.x3s))
            Text(
                text = account.email,
                style = typography.subHeading2.copy(
                    color = colorScheme.textSubheading,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (showAccountActivity && account.isLoggedIn) {
            AppIcon(
                id = if (account.isActiveAccount) AppIcons.Selection.CircleSelected else AppIcons.Selection.CircleUnselected,
                contentDescription = "Select account",
                type = AppIconType.Primary,
                modifier = Modifier.size(24.dp),
            )
        } else if (account.isExpired) {
            AppButton(
                label = AppUserStrings.LogInButton,
                onClick = onLoginRequest,
                type = ButtonType.TextPrimary,
                size = ButtonSize.Small,
                textTransform = TextTransform.NONE,
            )
        }

    }
}

@PreviewTheme
@Composable
fun AppUserPreview() {
    MeAppTheme {
        Column {
            AppUser(
                account = Account(
                    id = "1",
                    firstName = "Kristin",
                    lastName = "Jones",
                    dob = "1990-01-01",
                    email = "kristin@gmail.com",
                    gender = "Female",
                    isActiveAccount = true,
                    isLoggedIn = true,
                    lastActiveTime = "2024-01-15T10:30:00.000Z",
                    zipcode = "12345",
                    isSynced = true,
                    isExpired = false,
                    weightUnit = "lbs",
                    isWeightlessOn = true,
                    height = 170,
                    activityLevel = "Active",
                    weightlessTimestamp = "2024-01-15T10:30:00.000Z",
                    weightlessWeight = 65.5f,
                    isStreakOn = true,
                    dashboardType = "Dashboard4",
                    dashboardMetrics = listOf("weight", "bmi", "bodyfat"),
                ),
                onAccountSelect = {},
                onLoginRequest = {},
            )
            AppUser(
                account = Account(
                    id = "2",
                    firstName = "William",
                    lastName = "Smith",
                    dob = "1990-01-01",
                    email = "william@gmail.com",
                    gender = "Male",
                    isActiveAccount = false,
                    isLoggedIn = false,
                    lastActiveTime = "2024-01-15T10:30:00.000Z",
                    zipcode = "12345",
                    isSynced = false,
                    isExpired = true,
                    weightUnit = null,
                    isWeightlessOn = false,
                    height = null,
                    activityLevel = null,
                    weightlessTimestamp = null,
                    weightlessWeight = null,
                    isStreakOn = false,
                    dashboardType = null,
                    dashboardMetrics = null,
                ),
                onAccountSelect = {},
                onLoginRequest = {},
            )
        }
    }
}
