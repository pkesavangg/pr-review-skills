package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.helper.ScaleDataHelper
import com.dmdbrands.gurus.weight.features.common.strings.AppPopupStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

@Composable
fun ScaleDiscoveredModal(
  sku: String,
  modifier: Modifier = Modifier,
  onConnect: () -> Unit,
  onClose: () -> Unit = {},
) {
  // Map SKU for display (e.g., 0022 -> 0383) for SCALES lookup and image
  val scaleInfo = ScaleDataHelper.findScaleInfoBySku(sku) ?: return
  val displaySku = scaleInfo.sku
  val scaleName = scaleInfo.productName
  Box(
    modifier = modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier.align(Alignment.TopEnd)
    ){
      AppIcon(
        id = AppIcons.Default.closeFilled,
        contentDescription = AppPopupStrings.ScaleDiscoveredPopup.CloseContentDescription,
        modifier = Modifier
          .padding( top = spacing.md,end = spacing.sm)
          .size(24.dp),
        type = AppIconType.Primary,
        tintColor = Color.Unspecified,
        onClick = onClose
      )
    }

    // Main content with padding
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = spacing.x3l,bottom = spacing.x3l, start = spacing.x2l, end = spacing.x2l),
      verticalArrangement = Arrangement.spacedBy(spacing.sm),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // Main content
      AppScaleImage(
        sku = displaySku,
        scaleImageSize = ScaleImageSize.Large,
        modifier = Modifier.padding(top = spacing.sm),
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
        label = AppPopupStrings.ScaleDiscoveredPopup.ConnectButton,
        type = ButtonType.PrimaryFilled,
        onClick = onConnect,
      )
    }
  }
}

@PreviewTheme
@Composable
fun ScaleDiscoveredModalPreview() {
  MeAppTheme {
    ScaleDiscoveredModal(
      sku = "0412",
      onConnect = {},
      onClose = {},
    )
  }
}
