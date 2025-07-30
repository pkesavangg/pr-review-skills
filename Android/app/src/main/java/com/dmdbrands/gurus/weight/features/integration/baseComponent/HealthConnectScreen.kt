package com.dmdbrands.gurus.weight.features.integration.baseComponent

import androidx.compose.foundation.Image
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Data class to hold the content for a Health Connect screen
 */
data class HealthConnectScreenContent(
  val image: Int,
  val title: String,
  val description: String,
  val primaryButtonLabel: String,
  val secondaryButtonLabel: String? = null
)

/**
 * A common composable for Health Connect setup screens.
 *
 * @param content The content to display on the screen
 * @param onPrimaryAction Callback when the primary button is clicked
 * @param onSecondaryAction Callback when the secondary button is clicked, null if no secondary action
 * @param modifier The modifier to be applied to the composable
 */
@Composable
fun HealthConnectScreen(
  modifier: Modifier = Modifier,
  content: HealthConnectScreenContent,
  onPrimaryAction: () -> Unit,
  onSecondaryAction: (() -> Unit)? = null
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = MeTheme.spacing.md),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Image(
      painter = painterResource(id = content.image),
      contentDescription = null,
      modifier = Modifier
        .width(190.dp)
        .height(350.dp)
        .padding(top = MeTheme.spacing.md),
    )
    Spacer(Modifier.padding(top = MeTheme.spacing.lg))
    AppText(
      text = content.title,
      textType = TextType.Title,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.padding(top = MeTheme.spacing.x2s))
    AppText(
      text = content.description,
      textType = TextType.Subtitle,
      textAlign = TextAlign.Center,
    )
    Spacer(
      Modifier.padding(
        bottom = if (onSecondaryAction != null) MeTheme.spacing.lg else MeTheme.spacing.x6l,
      ),
    )
    AppButton(
      type = ButtonType.PrimaryFilled,
      label = content.primaryButtonLabel,
      size = ButtonSize.Large,
      onClick = onPrimaryAction,
    )
    if (onSecondaryAction != null && content.secondaryButtonLabel != null) {
      AppButton(
        type = ButtonType.TextPrimary,
        label = content.secondaryButtonLabel,
        size = ButtonSize.Large,
        onClick = onSecondaryAction,
        modifier = Modifier.padding(top = MeTheme.spacing.sm),
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun HealthConnectScreenPreview() {
  MeAppTheme {
    Surface {
      HealthConnectScreen(
        content = HealthConnectScreenContent(
          image = AppIcons.Integrations.No_Permission,
          title = "Connect to Health Connect",
          description = "Allow access to sync your data",
          primaryButtonLabel = "Connect",
          secondaryButtonLabel = "Skip",
        ),
        onPrimaryAction = {},
        onSecondaryAction = {},
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}
