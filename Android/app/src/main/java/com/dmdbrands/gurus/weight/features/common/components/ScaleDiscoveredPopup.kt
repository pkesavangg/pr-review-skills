package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.Red
import androidx.compose.ui.text.style.TextAlign
import com.dmdbrands.gurus.weight.features.common.model.SCALES
import com.dmdbrands.gurus.weight.features.common.strings.AppPopupStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

@Composable
fun ScaleDiscoveredModal(
  sku: String,
  modifier: Modifier = Modifier,
  onConnect: () -> Unit,
) {

  val scaleName = SCALES.find { it.sku == sku }!!.productName
  Column(
    modifier = modifier.fillMaxWidth().padding(bottom = spacing.x3l),
    verticalArrangement = Arrangement.spacedBy(spacing.md),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    AppScaleImage(
      sku = sku,
      scaleImageSize = ScaleImageSize.Large,
      modifier = Modifier.padding(top = spacing.md),
    )
      AppText(
        text = AppPopupStrings.ScaleDiscoveredPopup.Title,
        textType = TextType.ListTitle2,
        textAlign = TextAlign.Center,
      )
      AppText(
        text = scaleName,
        textType = TextType.Body,
        textAlign = TextAlign.Center,
      )

    AppButton(
      label = "Connect",
      type = ButtonType.PrimaryFilled,
      onClick = onConnect,
    )
  }
}

@PreviewTheme
@Composable
fun ScaleDiscoveredModalPreview() {
  MeAppTheme {
    ScaleDiscoveredModal(
      sku = "0412",
      onConnect = {},
    )
  }
}
