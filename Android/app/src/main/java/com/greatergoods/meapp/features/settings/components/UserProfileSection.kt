package com.greatergoods.meapp.features.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.domain.model.Account
import com.greatergoods.meapp.features.common.components.AppProfileAvatar
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

@Composable
fun UserProfileSection(
    account: Account?,
    onEditProfileClick: () -> Unit,
) {
    val firstName = account?.firstName ?: " "
    val email = account?.email ?: ""

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Profile Image Placeholder
        AppProfileAvatar(firstName)
        Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
        Text(
            text = firstName,
            style = MeTheme.typography.heading4,
            color = MeTheme.colorScheme.textHeading,
        )
        Spacer(modifier = Modifier.height(MeTheme.spacing.x3s))
        Text(
            text = email,
            style = MeTheme.typography.body2,
            color = MeTheme.colorScheme.textBody,
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
                "1",
                "John",
                "Doe",
                dob = "1990-01-01",
                email = "john.mckinley@examplepetstore.com",
                gender = "m",
            ),
        ) { }
    }
}
