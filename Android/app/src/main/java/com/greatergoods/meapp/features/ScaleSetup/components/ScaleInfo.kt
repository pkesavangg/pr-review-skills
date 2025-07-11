package com.greatergoods.meapp.features.ScaleSetup.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.greatergoods.meapp.features.ScaleSetup.strings.ScaleSetupStrings
import com.greatergoods.meapp.features.common.components.AppButton
import com.greatergoods.meapp.features.common.components.AppScaleImage
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.ButtonSize
import com.greatergoods.meapp.features.common.components.ButtonType
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.ScaleImageSize
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.features.common.model.SCALES
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme.spacing

@Composable
fun ScaleInfo(
  sku: String,
  buttonText: String? = null,
  onButtonClick: (() -> Unit)? = null,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(top = spacing.x3l),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    val scaleName = SCALES.find { it -> it.sku == sku }?.productName
    AppScaleImage(sku = sku, scaleImageSize = ScaleImageSize.Large)
    Spacer(modifier = Modifier.height(spacing.lg))
    scaleName?.let { it ->
      AppText(text = ScaleSetupStrings.ScaleInfo.Title(sku), textType = TextType.ListTitle2)
      AppText(text = scaleName, textType = TextType.Body)
    }
    Spacer(modifier = Modifier.height(spacing.lg))
    AppText(text = ScaleSetupStrings.ScaleInfo.Subtitle, textType = TextType.Body, textAlign = TextAlign.Center)
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
    ScaleInfo("0412", buttonText = "Get your scale’s MAC address ", onButtonClick = {})
  }
}
