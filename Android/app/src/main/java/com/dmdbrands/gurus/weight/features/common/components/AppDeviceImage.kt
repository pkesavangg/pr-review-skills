package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import com.dmdbrands.gurus.weight.features.common.helper.DeviceUtility
import com.dmdbrands.gurus.weight.features.common.strings.DeviceStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.borderRadius
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

enum class DeviceImageSize { Small, Medium, Large }

object DeviceImageDefaults {
    val BABY_SCALE_SKUS = DeviceHelper.BABY_SCALE_SKUS

    fun size(size: DeviceImageSize): Dp =
        when (size) {
            DeviceImageSize.Small -> 75.dp
            DeviceImageSize.Medium -> 120.dp
            DeviceImageSize.Large -> 180.dp
        }

    fun monitorWidth(size: DeviceImageSize): Dp =
        when (size) {
            DeviceImageSize.Small -> 55.dp
            DeviceImageSize.Medium -> 90.dp
            DeviceImageSize.Large -> 140.dp
        }
}

@Composable
fun AppDeviceImage(
  sku: String,
  modifier: Modifier = Modifier,
  scaleImageSize: DeviceImageSize = DeviceImageSize.Small,
  // Only weight scales get the glow backdrop; baby scales and BPM monitors render flat (no bg).
  showShadow: Boolean =
    sku !in DeviceImageDefaults.BABY_SCALE_SKUS && !DeviceHelper.isBpmDevice(sku),
) {
  val isBpm = DeviceHelper.isBpmDevice(sku)
  val imageHeight = DeviceImageDefaults.size(scaleImageSize)
  val imageWidth = if (isBpm) DeviceImageDefaults.monitorWidth(scaleImageSize) else imageHeight

  // Apply the glow only when showShadow — previously it was applied unconditionally, so the
  // backdrop showed behind baby/balance images too.
  val shadowModifier = if (showShadow) {
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
  }

  Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    Box(
      modifier =
        Modifier
          .width(imageWidth)
          .height(imageHeight)
          .then(shadowModifier)
          .clip(RoundedCornerShape(borderRadius.xs)),
      contentAlignment = Alignment.Center,
    ) {
      Image(
        painter =
          painterResource(
            id = DeviceUtility.scaleImageResource(sku),
          ),
        contentDescription = if (isBpm) "$sku ${DeviceStrings.accMonitorImageSuffix}" else "$sku ${DeviceStrings.accScaleImageSuffix}",
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
      AppDeviceImage("0412", scaleImageSize = DeviceImageSize.Large)
      Spacer(Modifier.height(spacing.md))
      AppDeviceImage("0397", scaleImageSize = DeviceImageSize.Large)
      Spacer(Modifier.height(spacing.md))
      AppDeviceImage("0603", scaleImageSize = DeviceImageSize.Large)
      Spacer(Modifier.height(spacing.md))
      AppDeviceImage("0663", scaleImageSize = DeviceImageSize.Large)
    }
  }
}
