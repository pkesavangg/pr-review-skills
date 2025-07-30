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
 * A composable that displays the user conflict screen for Health Connect integration.
 *
 * @param onPrimaryAction Callback when the primary button is clicked
 * @param modifier The modifier to be applied to the composable
 */
@Composable
fun UserConflictScreen(
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier
) {
  HealthConnectScreen(
    content = HealthConnectScreenContent(
      image = AppIcons.Integrations.User_Conflict,
      title = HealthConnectStrings.UserConflictStrings.Title,
      description = HealthConnectStrings.UserConflictStrings.Description,
      primaryButtonLabel = HealthConnectStrings.ActionButtons.exit,
    ),
    onPrimaryAction = onPrimaryAction,
    modifier = modifier
  )
}

@Preview(showBackground = true)
@Composable
private fun UserConflictScreenPreview() {
    MeAppTheme {
        Surface {
            UserConflictScreen(
                onPrimaryAction = {},
                modifier = Modifier
            )
        }
    }
}
