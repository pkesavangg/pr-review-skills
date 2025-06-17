package com.greatergoods.meapp.features.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.features.common.components.AppButton
import com.greatergoods.meapp.features.common.components.AppIcon
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.settings.strings.SettingsScreenStrings
import com.greatergoods.meapp.features.settings.viewmodel.SettingsViewModel
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

@Composable
fun UserProfileSection(viewModel: SettingsViewModel = hiltViewModel()) {
    UserProfileContent({ viewModel.onEditProfileClick() })
}

@Composable
fun UserProfileContent(onEditProfileClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Profile Image Placeholder
        AppIcon(
            AppIcons.Default.Settings,
            contentDescription = SettingsScreenStrings.ProfileImage,
        )
        Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
        Text(
            text = "Kristin",
            style = MeTheme.typography.heading4,
            color = MeTheme.colorScheme.textHeading,
        )
        Text(
            text = "kstazrad@gmail.com",
            style = MeTheme.typography.body2,
            color = MeTheme.colorScheme.textBody,
        )
        Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
        AppButton(
            SettingsScreenStrings.Edit,
            onClick = { onEditProfileClick() },
        )
    }
}

@PreviewTheme
@Composable
fun UserProfileSectionPreview() {
    MeAppTheme {
        UserProfileContent({})
    }
}
