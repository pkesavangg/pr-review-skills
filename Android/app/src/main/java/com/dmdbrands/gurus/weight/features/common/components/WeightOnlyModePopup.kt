package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.strings.AppPopupStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

@Composable
fun WeightOnlyModePopup(
  modifier: Modifier = Modifier,
  onEnable: () -> Unit,
  onDismiss: () -> Unit
) {

  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(bottom = spacing.x3l),
    verticalArrangement = Arrangement.spacedBy(spacing.md),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {

    AppIcon(
      id = AppIcons.Default.WeightOnlyMode,
      contentDescription = "Weightonlymode",
      modifier = Modifier.size(60.dp),
      type = AppIconType.Primary,
    )

    AppText(
      text = AppPopupStrings.WeightOnlyModeEnabledPopup.Title,
      textType = TextType.ListTitle2,
      textAlign = TextAlign.Center,
    )
    AppText(
      text = AppPopupStrings.WeightOnlyModeEnabledPopup.Message,
      textType = TextType.Body,
      textAlign = TextAlign.Center,
    )

    AppButton(
      label = AppPopupStrings.WeightOnlyModeEnabledPopup.ConfirmButton,
      type = ButtonType.PrimaryFilled,
      onClick = onEnable,
    )

    AppButton(
      label = AppPopupStrings.WeightOnlyModeEnabledPopup.CancelButton,
      type = ButtonType.TextPrimary,
      onClick = onDismiss,
    )
  }
}

@PreviewTheme
@Composable
fun WeightOnlyModeEnabledPopupPreview() {
  MeAppTheme {
    WeightOnlyModePopup(
      onEnable = {},
      onDismiss = {},
    )
  }
}
