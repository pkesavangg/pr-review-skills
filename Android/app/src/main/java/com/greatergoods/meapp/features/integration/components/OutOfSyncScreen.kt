package com.greatergoods.meapp.features.integration.components

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.greatergoods.meapp.features.integration.baseComponent.HealthConnectScreen
import com.greatergoods.meapp.features.integration.baseComponent.HealthConnectScreenContent
import com.greatergoods.meapp.features.integration.strings.HealthConnectStrings
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme

/**
 * A composable that displays the out of sync screen for Health Connect integration.
 *
 * @param onPrimaryAction Callback when the primary button is clicked
 * @param onSecondaryAction Callback when the secondary button is clicked
 * @param modifier The modifier to be applied to the composable
 */
@Composable
fun OutOfSyncScreen(
    onPrimaryAction: () -> Unit,
    onSecondaryAction: () -> Unit,
    modifier: Modifier = Modifier
) {
  HealthConnectScreen(
    content = HealthConnectScreenContent(
      image = AppIcons.Integrations.Health_Connect_Off,
      title = HealthConnectStrings.outOfSyncAlert.title,
      description = HealthConnectStrings.outOfSyncAlert.description,
      primaryButtonLabel = HealthConnectStrings.ActionButtons.openHealthConnect,
      secondaryButtonLabel = HealthConnectStrings.ActionButtons.removeIntegration
    ),
    onPrimaryAction = onPrimaryAction,
    onSecondaryAction = onSecondaryAction,
    modifier = modifier
  )
}

@Preview(showBackground = true)
@Composable
private fun OutOfSyncScreenPreview() {
    MeAppTheme {
        Surface {
            OutOfSyncScreen(
                onPrimaryAction = {},
                onSecondaryAction = {},
                modifier = Modifier
            )
        }
    }
}
