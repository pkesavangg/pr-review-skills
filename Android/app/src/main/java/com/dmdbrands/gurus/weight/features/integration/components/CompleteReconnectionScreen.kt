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
 * A composable that displays the complete reconnection screen for Health Connect integration.
 *
 * @param onPrimaryAction Callback when the finish button is clicked
 * @param modifier The modifier to be applied to the composable
 */
//start full permission flow
@Composable
fun CompleteReconnectionScreen(
  modifier: Modifier = Modifier,
  onPrimaryAction: () -> Unit,
) {
  HealthConnectScreen(
    content = HealthConnectScreenContent(
      image = AppIcons.Integrations.Full_Permission,
      title = HealthConnectStrings.StartFullReconnectStrings.Title,
      description = HealthConnectStrings.StartFullReconnectStrings.Description,
      annotatedString = HealthConnectStrings.StartFullReconnectStrings.AnnonatedDesc,
      primaryButtonLabel = HealthConnectStrings.ActionButtons.finish
    ),
    onPrimaryAction = onPrimaryAction,
    modifier = modifier
  )
}

@Preview(showBackground = true)
@Composable
private fun CompleteReconnectionScreenPreview() {
    MeAppTheme {
        Surface {
            CompleteReconnectionScreen(
              onPrimaryAction = {},
              modifier = Modifier
            )
        }
    }
}
