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

@Composable
fun StartConnect(
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    HealthConnectScreen(
        modifier = modifier,
        content = HealthConnectScreenContent(
            image = AppIcons.Integrations.No_Permission,
            title = HealthConnectStrings.StartConnectStrings.Title,
            description = HealthConnectStrings.StartConnectStrings.Description,
            primaryButtonLabel = HealthConnectStrings.ActionButtons.connect,
        ),
        onPrimaryAction = onPrimaryAction,
    )
}

@Preview(showBackground = true)
@Composable
private fun StartConnectPreview() {
    MeAppTheme {
        Surface {
            StartConnect(
                onPrimaryAction = {},
                modifier = Modifier
            )
        }
    }
}
