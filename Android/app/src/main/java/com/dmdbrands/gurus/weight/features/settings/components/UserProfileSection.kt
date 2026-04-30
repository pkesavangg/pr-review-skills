package com.dmdbrands.gurus.weight.features.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.features.common.components.AppProfileAvatar
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

@Composable
fun UserProfileSection(
    account: Account?,
    onEditProfileClick: () -> Unit,
    onAvatarLongPress: (() -> Unit)? = null,
) {
    val firstName = account?.firstName ?: " "
    val email = account?.email ?: ""

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Profile Image Placeholder
        AppProfileAvatar(
            text = firstName,
            onLongPress = onAvatarLongPress
        )
        Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
        Text(
            text = firstName,
            style = MeTheme.typography.heading4,
            color = MeTheme.colorScheme.textHeading,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(MeTheme.spacing.x3s))
        Text(
            text = email,
            style = MeTheme.typography.body2,
            color = MeTheme.colorScheme.textBody,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(MeTheme.spacing.xs))
    }
}

@PreviewTheme
@Composable
fun UserProfileSectionPreview() {
    MeAppTheme {
        UserProfileSection(
            Account(
                id = "1",
                firstName = "cjxbhczxbcnzxmbczxkcbskfksdfdsjhfjdhfjsdhfksdhjfdjkfdskfksdjfjkshdf",
                lastName = "Doe",
                dob = "1990-01-01",
                email = "john.mckinley@examplepetstore.com",
                expiresAt = null,
                fcmToken = null,
                gender = "m",
                isActiveAccount = true,
                isLoggedIn = true,
                isExpired = false,
                isSynced = true,
                lastActiveTime = "1234567890",
                zipcode = "12345",
                weightUnit = WeightUnit.LB,
                isWeightlessOn = false,
            height = null,
            activityLevel = null,
            weightlessTimestamp = null,
            weightlessWeight = null,
            isStreakOn = false,
            dashboardType = null,
            dashboardMetrics = null,
            ),
            onEditProfileClick = {},
            onAvatarLongPress = null
        )
    }
}
