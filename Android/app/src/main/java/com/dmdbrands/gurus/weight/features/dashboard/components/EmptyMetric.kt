package com.dmdbrands.gurus.weight.features.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.dashboard.string.DashboardString
import com.dmdbrands.gurus.weight.theme.MeTheme

@Composable
fun EmptyMetric(onConnectScaleClick: () -> Unit) {
  val lang = DashboardString.EmptyMetric
  Column(
    modifier = Modifier.padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    AppText(
      text = lang.description,
      textType = TextType.Body,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = MeTheme.spacing.sm, bottom = MeTheme.spacing.lg),
    )
    AppButton(
      label = lang.connectScale,
      onClick = { onConnectScaleClick() },
    )
  }
}
