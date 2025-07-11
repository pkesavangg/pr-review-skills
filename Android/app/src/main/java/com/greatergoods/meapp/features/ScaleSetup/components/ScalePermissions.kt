package com.greatergoods.meapp.features.ScaleSetup.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.features.ScaleSetup.strings.ScaleSetupStrings
import com.greatergoods.meapp.features.appPermissions.helper.PermissionGroup
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.features.common.enums.ScaleSetupType
import com.greatergoods.meapp.features.permissionSettings.PermissionSettings
import com.greatergoods.meapp.theme.MeTheme.spacing

@Composable
fun ScalePermissions(
    scaleType: ScaleSetupType,
    permissionGroups: List<PermissionGroup>,
    onRequestPermission: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = spacing.sm, horizontal = spacing.md),
    ) {
        AppText(
            text = ScaleSetupStrings.ScalePermissions.Title,
            textType = TextType.ListTitle1,
            modifier = Modifier.padding(bottom = spacing.xs),
        )
        AppText(
            text = ScaleSetupStrings.ScalePermissions.Subtitle(scaleType),
            textType = TextType.Body,
            modifier = Modifier.padding(bottom = spacing.lg),
        )
        PermissionSettings(
            permissionGroups = permissionGroups,
            onRequestPermission = onRequestPermission,
        )

    }
}

