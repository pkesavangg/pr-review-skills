package com.dmdbrands.gurus.weight.features.ScaleSetup.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.dmdbrands.gurus.weight.features.common.model.SCALES
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
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = spacing.sm, vertical = spacing.md),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    val scaleName = SCALES.find { it.sku == sku }?.productName
    AppScaleImage(sku = sku, scaleImageSize = ScaleImageSize.Large)
    Spacer(modifier = Modifier.height(spacing.lg))
    scaleName?.let {
      AppText(text = ScaleSetupStrings.ScaleInfo.Title(sku), textType = TextType.ListTitle2)
      Spacer(modifier = Modifier.height(spacing.xs))
      AppText(text = scaleName, textType = TextType.Body)
    }
    Spacer(modifier = Modifier.height(spacing.lg))
    AppText(
      text = ScaleSetupStrings.ScaleInfo.Subtitle,
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

@PreviewTheme()
@Composable
fun ScaleInfoPreview() {
  MeAppTheme {
    ScaleInfo("0412", buttonText = "Get your scale’s MAC address ", setupType = ScaleSetupType.Lcbt, onButtonClick = {})
  }
}
