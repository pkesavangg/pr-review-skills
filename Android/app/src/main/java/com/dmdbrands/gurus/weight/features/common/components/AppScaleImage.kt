package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper
import com.dmdbrands.gurus.weight.features.common.helper.ScaleUtility
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.borderRadius
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

enum class ScaleImageSize { Small, Medium, Large }

object AppScaleImageDefaults {
    val BABY_SCALE_SKUS = DeviceHelper.BABY_SCALE_SKUS
}

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
  showShadow: Boolean = sku !in AppScaleImageDefaults.BABY_SCALE_SKUS,
) {
  Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    Box(
      modifier =
        Modifier
          .size(ScaleImageDefaults.size(scaleImageSize))
          .then(
            if (showShadow) {
              Modifier.dropShadow(
                shape = RoundedCornerShape(borderRadius.sm),
                shadow = Shadow(
                  radius = spacing.sm,
                  spread = (-4).dp,
                  color = MeTheme.colorScheme.glow,
                  offset = DpOffset(x = 0.dp, 0.dp),
                ),
              )
            } else {
              Modifier
            },
          ).clip(RoundedCornerShape(borderRadius.xs)),
      contentAlignment = Alignment.Center,
    ) {
      val context = androidx.compose.ui.platform.LocalContext.current
      Image(
        painter =
          painterResource(
            id = ScaleUtility.scaleImageResource(context, sku),
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
