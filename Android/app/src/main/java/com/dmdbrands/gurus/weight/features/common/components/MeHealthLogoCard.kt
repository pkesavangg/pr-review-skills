package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

/** Static text for the shared brand logo card. */
object MeHealthLogoStrings {
  const val ByGreaterGoods = "By Greater Goods"
  const val AccLogoLabel = "my everyday health by Greater Goods"
}

/**
 * The Phase 2 brand logo card used on the Loading and Landing screens (Figma 32224-28400):
 * the "my everyday health" wordmark over a "By Greater Goods" subtitle, on a white rounded card.
 */
@Composable
fun MeHealthLogoCard(modifier: Modifier = Modifier) {
  Column(
    modifier = modifier
      .background(
        color = MeTheme.colorScheme.primaryBackground,
        shape = RoundedCornerShape(MeTheme.borderRadius.lg),
      )
      // TalkBack: read the whole card as the brand name.
      .clearAndSetSemantics { contentDescription = MeHealthLogoStrings.AccLogoLabel }
      .padding(horizontal = MeTheme.spacing.x2l, vertical = MeTheme.spacing.xl),
    horizontalAlignment = Alignment.Start,
    verticalArrangement = Arrangement.Center,
  ) {
    Image(
      painter = painterResource(id = AppIcons.Default.MeHealthLogo),
      contentDescription = null,
      modifier = Modifier.width(210.dp),
    )
    Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
    Text(
      text = MeHealthLogoStrings.ByGreaterGoods,
      style = MeTheme.typography.body4,
      color = MeTheme.colorScheme.textSubheading,
    )
  }
}

@PreviewTheme
@Composable
private fun MeHealthLogoCardPreview() {
  MeAppTheme {
    MeHealthLogoCard()
  }
}
