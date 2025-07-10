package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.features.common.helper.ScaleUtility
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme.borderRadius
import com.greatergoods.meapp.theme.MeTheme.colorScheme
import com.greatergoods.meapp.theme.MeTheme.spacing

enum class ScaleImageSize { Small, Medium, Large }

object ScaleImageDefaults {
    fun size(size: ScaleImageSize): Dp =
        when (size) {
            ScaleImageSize.Small -> 75.dp
            ScaleImageSize.Medium -> 120.dp
            ScaleImageSize.Large -> 180.dp
        }
}

@Composable
fun AppScaleImage(
  sku: String,
  modifier: Modifier = Modifier,
  scaleImageSize: ScaleImageSize = ScaleImageSize.Small,
) {
  // TODO: Update color tokens for glow effect
  Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    Box(
      modifier =
        Modifier
          .size(ScaleImageDefaults.size(scaleImageSize))
          .shadow(
              shape = RoundedCornerShape(borderRadius.sm),
            elevation = spacing.sm,
            spotColor = colorScheme.glow,
            ambientColor = colorScheme.glow,
          ),
      contentAlignment = Alignment.Center,
    ) {
      Image(
        painter =
          painterResource(
            id = ScaleUtility.scaleImageResource(sku),
          ),
        contentDescription = "$sku scale",
      )
    }
  }
}

@PreviewTheme
@Composable
fun PreviewAppScaleImage() {
  MeAppTheme {
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Spacer(Modifier.height(spacing.md))
      AppScaleImage("0412", scaleImageSize = ScaleImageSize.Large)
      Spacer(Modifier.height(spacing.md))
      AppScaleImage("0397", scaleImageSize = ScaleImageSize.Large)
    }
  }
}
