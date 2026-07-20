package com.dmdbrands.gurus.weight.features.integration.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
 * A composable that displays the multiple device connection screen for Health Connect integration.
 *
 * @param onPrimaryAction Callback when the primary button is clicked
 * @param modifier The modifier to be applied to the composable
 */
@Composable
fun MultipleDeviceConnectionScreen(
  modifier: Modifier = Modifier,
  secondaryActionLabel: String? = null,
  onClose: () -> Unit,
  onPrimaryAction: () -> Unit,
  onSecondaryAction: (() -> Unit)? = null
) {
  BaseModal {
    Box {
      AppIcon(
        id = AppIcons.Filled.Close,
        contentDescription = HealthConnectStrings.Accessibility.closeButton,
        modifier = Modifier
          .align(Alignment.TopEnd).padding(bottom = spacing.md),
        type = com.dmdbrands.gurus.weight.features.common.components.AppIconType.Default,
        onClick = onClose
      )
      MultipleDeviceConnectionContent(
        modifier = modifier,
        secondaryActionLabel = secondaryActionLabel,
        onPrimaryAction = onPrimaryAction,
        onSecondaryAction = onSecondaryAction,
      )
    }
  }
}

@Composable
private fun MultipleDeviceConnectionContent(
  modifier: Modifier = Modifier,
  secondaryActionLabel: String? = null,
  onPrimaryAction: () -> Unit,
  onSecondaryAction: (() -> Unit)? = null
) {
  Column(
    modifier = modifier
      .fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Image(
      painter = painterResource(id = AppIcons.Integrations.Health_Connect_Logo),
      contentDescription = null,
      modifier = Modifier
        .width(190.dp)
        .height(100.dp)
        .padding(top = spacing.lg),
    )
    Spacer(Modifier.padding(top = spacing.lg))
    // TalkBack: status title is the heading, announced politely on appearance.
    AppText(
      text = HealthConnectStrings.AddHealthConnectStrings.Title,
      textType = TextType.Title,
      textAlign = TextAlign.Center,
      modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.padding(top = MeTheme.spacing.sm))
    AppText(
      text = HealthConnectStrings.AddHealthConnectStrings.Description,
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
      label = HealthConnectStrings.ActionButtons.connect,
      size = ButtonSize.Large,
      onClick = onPrimaryAction,
    )
    if (onSecondaryAction !== null && secondaryActionLabel !== null) {
      AppButton(
        type = ButtonType.TextPrimary,
        label = secondaryActionLabel,
        size = ButtonSize.Large,
        onClick = { onSecondaryAction.invoke() },
        modifier = Modifier.padding(top = MeTheme.spacing.sm),
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun MultipleDeviceConnectionScreenPreview() {
    MeAppTheme {
        Surface {
            MultipleDeviceConnectionScreen(
                onPrimaryAction = {},
                onSecondaryAction = {},
                onClose = {},
                modifier = Modifier
            )
        }
    }
}
