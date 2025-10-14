package com.dmdbrands.gurus.weight.features.integration.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppIcon
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.BaseModal
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.integration.strings.HealthConnectStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

/**
 * A composable that displays the out of sync screen for Health Connect integration.
 *
 * @param onPrimaryAction Callback when the primary button is clicked
 * @param onSecondaryAction Callback when the secondary button is clicked
 * @param modifier The modifier to be applied to the composable
 */
@Composable
fun OutOfSyncScreen(
    onClose: () -> Unit,
    onPrimaryAction: () -> Unit,
    onSecondaryAction: () -> Unit,
    modifier: Modifier = Modifier
) {
  Dialog(
    onDismissRequest = onClose,
    properties = DialogProperties(
      dismissOnBackPress = true,
      dismissOnClickOutside = true,
      usePlatformDefaultWidth = false,
      decorFitsSystemWindows = false,
    ),
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(MeTheme.colorScheme.glow),
    ) {
      Box(modifier = Modifier.align(Alignment.Center)) {
        BaseModal {
          Box {
            AppIcon(
              id = AppIcons.Filled.Close,
              contentDescription = "Close",
              modifier = Modifier
                .align(Alignment.TopEnd).padding(bottom = spacing.md),
              type = com.dmdbrands.gurus.weight.features.common.components.AppIconType.Default,
              onClick = onClose
            )
            Column(
              modifier = modifier
                .fillMaxWidth(),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Image(
                painter = painterResource(id = AppIcons.Integrations.Health_Connect_Off),
                contentDescription = null,
                modifier = Modifier
                  .width(190.dp)
                  .height(100.dp)
                  .padding(top = MeTheme.spacing.lg),
              )
              Spacer(Modifier.padding(top = MeTheme.spacing.lg))
              AppText(
                text = HealthConnectStrings.PopupStrings.outOfSyncTitle,
                textType = TextType.Title,
                textAlign = TextAlign.Center,
              )
              Spacer(Modifier.padding(top = MeTheme.spacing.sm))
              AppText(
                text = HealthConnectStrings.PopupStrings.outOfSyncDescription,
                textType = TextType.Subtitle,
                textAlign = TextAlign.Center,
              )
              Spacer(
                Modifier.padding(
                  bottom = MeTheme.spacing.lg,
                ),
              )
              AppButton(
                type = ButtonType.PrimaryFilled,
                label = HealthConnectStrings.ActionButtons.openHealthConnect,
                size = ButtonSize.Large,
                onClick = onPrimaryAction,
              )
              AppButton(
                type = ButtonType.TextPrimary,
                label = HealthConnectStrings.ActionButtons.removeIntegration,
                size = ButtonSize.Large,
                onClick = { onSecondaryAction.invoke() },
                modifier = Modifier.padding(top = MeTheme.spacing.sm),
              )
            }
          }
        }
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun OutOfSyncScreenPreview() {
    MeAppTheme {
        Surface {
            OutOfSyncScreen(
                onPrimaryAction = {},
                onSecondaryAction = {},
                onClose = {}
            )
        }
    }
}
