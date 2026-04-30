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
 * A composable that displays the incomplete reconnection screen for Health Connect integration.
 *
 * @param onPrimaryAction Callback when the primary button is clicked
 * @param onSecondaryAction Callback when the secondary button is clicked
 * @param modifier The modifier to be applied to the composable
 */
@Composable
fun IncompleteReconnectionScreen(
    modifier: Modifier = Modifier,
    onPrimaryAction: () -> Unit,
    onSecondaryAction: () -> Unit,
) {
  HealthConnectScreen(
    content = HealthConnectScreenContent(
      image = AppIcons.Integrations.Full_Permission,
      title = HealthConnectStrings.PartialReconnectStrings.Title,
      description = HealthConnectStrings.PartialReconnectStrings.Description,
      annotatedString = HealthConnectStrings.PartialReconnectStrings.AnnonatedDesc,
      primaryButtonLabel = HealthConnectStrings.ActionButtons.updatePermissions,
      secondaryButtonLabel = HealthConnectStrings.ActionButtons.skip
    ),
    onPrimaryAction = onPrimaryAction,
    onSecondaryAction = onSecondaryAction,
    modifier = modifier
  )
}

@Preview(showBackground = true)
@Composable
private fun IncompleteReconnectionScreenPreview() {
    MeAppTheme {
        Surface {
            IncompleteReconnectionScreen(
                onPrimaryAction = {},
                onSecondaryAction = {},
                modifier = Modifier
            )
        }
    }
}
