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
 * A composable that displays the finish connect screen for Health Connect integration.
 *
 * @param onPrimaryAction Callback when the finish button is clicked
 * @param modifier The modifier to be applied to the composable
 */
@Composable
fun FinishConnect(
    modifier: Modifier = Modifier,
    title: String = HealthConnectStrings.FinishConnectStrings.Title,
    image:Int = AppIcons.Integrations.HC_Homepage,
    onPrimaryAction: () -> Unit,
) {
    HealthConnectScreen(
      content = HealthConnectScreenContent(
        image = image,
        title = title,
        description = HealthConnectStrings.FinishConnectStrings.Description,
        primaryButtonLabel = HealthConnectStrings.ActionButtons.finish,
      ),
      onPrimaryAction = onPrimaryAction,
      modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
private fun FinishConnectPreview() {
    MeAppTheme {
        Surface {
            FinishConnect(
                onPrimaryAction = {},
                modifier = Modifier
            )
        }
    }
}
