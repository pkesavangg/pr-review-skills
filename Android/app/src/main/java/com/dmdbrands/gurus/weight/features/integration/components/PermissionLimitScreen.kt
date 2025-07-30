package com.dmdbrands.gurus.weight.features.integration.components

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.dmdbrands.gurus.weight.features.integration.baseComponent.HealthConnectScreen
import com.dmdbrands.gurus.weight.features.integration.baseComponent.HealthConnectScreenContent
import com.dmdbrands.gurus.weight.features.integration.strings.HealthConnectStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme

/**
 * A composable that displays the permission limit screen for Health Connect integration.
 *
 * @param onPrimaryAction Callback when the primary button is clicked
 * @param modifier The modifier to be applied to the composable
 */
@Composable
fun PermissionLimitScreen(
    onPrimaryAction: () -> Unit,
    onSecondaryAction: () -> Unit,
    modifier: Modifier = Modifier
) {
  HealthConnectScreen(
    content = HealthConnectScreenContent(
      image = AppIcons.Integrations.Permission_Failed,
      title = HealthConnectStrings.IntegrationFailedStrings.Title,
      description = HealthConnectStrings.IntegrationFailedStrings.Description,
      primaryButtonLabel = HealthConnectStrings.ActionButtons.openHealthConnect,
      secondaryButtonLabel = HealthConnectStrings.ActionButtons.exit
    ),
    onPrimaryAction = onPrimaryAction,
    onSecondaryAction = onSecondaryAction,
    modifier = modifier
  )
}

@Preview(showBackground = true)
@Composable
private fun PermissionLimitScreenPreview() {
    MeAppTheme {
        Surface {
            PermissionLimitScreen(
                onPrimaryAction = {},
                onSecondaryAction = {},
                modifier = Modifier
            )
        }
    }
}
