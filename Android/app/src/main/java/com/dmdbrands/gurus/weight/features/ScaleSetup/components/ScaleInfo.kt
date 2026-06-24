package com.dmdbrands.gurus.weight.features.ScaleSetup.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.ScaleSetupStrings
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppScaleImage
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.ScaleImageSize
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper
import com.dmdbrands.gurus.weight.features.common.helper.ScaleDataHelper
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

@Composable
fun ScaleInfo(
  sku: String,
  setupType: ScaleSetupType,
  modifier: Modifier = Modifier,
  buttonText: String? = null,
  onButtonClick: (() -> Unit)? = null,
) {
  // Map SKU for display (e.g., 0022 -> 0383)
  val scaleInfo = ScaleDataHelper.findScaleInfoBySku(sku)
  // mapSkuForDisplay is null-safe; for display fall back to the original sku, then to empty.
  val displaySku = scaleInfo?.sku ?: DeviceHelper.mapSkuForDisplay(sku) ?: sku
  // Vertically centre the device-info content (MOB-970). A plain
  // Modifier.fillMaxSize().verticalScroll() + Arrangement.Center does NOT centre —
  // verticalScroll relaxes the height to infinity, so the column wraps its content
  // and Arrangement.Center is ignored (content sticks to the top). Forcing the
  // scrollable column to be at least the viewport tall (heightIn min = maxHeight)
  // makes Arrangement.Center centre when content fits, and still scroll when it overflows.
  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = maxHeight)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = spacing.sm, vertical = spacing.md),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      val scaleName = scaleInfo?.productName
    val isBabyScale = setupType == ScaleSetupType.BabyScale
    AppScaleImage(sku = displaySku, scaleImageSize = ScaleImageSize.Large, showShadow = !isBabyScale)
    Spacer(modifier = Modifier.height(spacing.lg))
    scaleName?.let {
      AppText(
        text = if (isBabyScale) ScaleSetupStrings.ScaleInfo.BabyScaleTitle
        else ScaleSetupStrings.ScaleInfo.Title(displaySku),
        textType = TextType.ListTitle2,
      )
      Spacer(modifier = Modifier.height(spacing.xs))
      AppText(text = scaleName, textType = TextType.Body)
    }
    Spacer(modifier = Modifier.height(spacing.lg))
    AppText(
      text = if (setupType == ScaleSetupType.BabyScale) {
        ScaleSetupStrings.ScaleInfo.BabyScaleSubtitle
      } else if(setupType == ScaleSetupType.BpmBluetooth || setupType == ScaleSetupType.BpmA6Bluetooth) {
        ScaleSetupStrings.ScaleInfo.MonitorSubtitle
      }else {
        ScaleSetupStrings.ScaleInfo.Subtitle
      },
      textType = TextType.Body,
      textAlign = TextAlign.Left,
      modifier = Modifier.fillMaxWidth(),
    )
    if (buttonText != null && onButtonClick != null) {
      Spacer(modifier = Modifier.height(spacing.lg))
      AppButton(
        label = buttonText,
        type = ButtonType.InlineTextPrimary,
        size = ButtonSize.Small,
        onClick = onButtonClick,
        modifier = Modifier.align(Alignment.CenterHorizontally),
      )
      }
    }
  }
}

@PreviewTheme()
@Composable
fun ScaleInfoPreview() {
  MeAppTheme {
    ScaleInfo("0412", buttonText = "Get your scale’s MAC address ", setupType = ScaleSetupType.Lcbt, onButtonClick = {})
  }
}
