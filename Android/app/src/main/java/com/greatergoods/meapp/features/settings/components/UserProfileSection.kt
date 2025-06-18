package com.greatergoods.meapp.features.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.features.common.components.AppButton
import com.greatergoods.meapp.features.common.components.AppProfileAvatar
import com.greatergoods.meapp.features.common.components.ButtonSize
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.settings.strings.SettingsScreenStrings
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

@Composable
fun UserProfileSection(onEditProfileClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Profile Image Placeholder
        AppProfileAvatar("Vivek")
        Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
        Text(
            text = "Kristin",
            style = MeTheme.typography.heading4,
            color = MeTheme.colorScheme.textHeading,
        )
        Spacer(modifier = Modifier.height(MeTheme.spacing.x3s))
        Text(
            text = "kstazrad@gmail.com",
            style = MeTheme.typography.body2,
            color = MeTheme.colorScheme.textBody,
        )
        Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
        AppButton(
            SettingsScreenStrings.Edit,
            size = ButtonSize.Small,
            onClick = { onEditProfileClick() },
        )
    }
}

@PreviewTheme
@Composable
fun UserProfileSectionPreview() {
    MeAppTheme {
        UserProfileSection {  }
    }
}
