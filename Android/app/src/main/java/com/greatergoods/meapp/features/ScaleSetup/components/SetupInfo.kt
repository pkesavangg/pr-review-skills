package com.greatergoods.meapp.features.ScaleSetup.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.greatergoods.meapp.features.ScaleSetup.strings.ScaleSetupStrings
import com.greatergoods.meapp.features.common.components.AppScaleImage
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.ScaleImageSize
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.features.common.model.SCALES
import com.greatergoods.meapp.features.common.model.ScaleInfo
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme.spacing

@Composable
fun SetupInfo(sku: String) {
  Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    val scaleName = SCALES.find { it -> it.sku == sku }?.productName
    AppScaleImage(sku = sku, scaleImageSize = ScaleImageSize.Large)
    Spacer(modifier = Modifier.height(spacing.lg))
    scaleName?.let { it ->
      AppText(text = ScaleSetupStrings.SetupInfo.Title(sku), textType = TextType.ListTitle2)
      AppText(text = scaleName, textType = TextType.Body)
    }
    Spacer(modifier = Modifier.height(spacing.lg))
    AppText(text = ScaleSetupStrings.SetupInfo.Subtitle, textType = TextType.Body, textAlign = TextAlign.Center)
  }
}

@PreviewTheme()
@Composable
fun SetupInfoPreview() {
  MeAppTheme {
    SetupInfo("0412")
  }
}
