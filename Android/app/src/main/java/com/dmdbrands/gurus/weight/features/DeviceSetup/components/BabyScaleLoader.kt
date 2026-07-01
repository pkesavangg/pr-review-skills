package com.dmdbrands.gurus.weight.features.DeviceSetup.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.R
import com.dmdbrands.gurus.weight.features.DeviceSetup.strings.BabyScaleSetupStrings
import com.dmdbrands.gurus.weight.features.common.components.AppGifImage
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

private val LoaderSize = 125.dp

/**
 * Baby scale loader that displays the animated baby app loader GIF
 * with title and subtitle.
 */
@Composable
fun BabyScaleLoader(
  title: String,
  subtitle: String,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    AppText(
      text = title,
      textType = TextType.Title,
      textAlign = TextAlign.Center,
      // TalkBack: loader title is the heading.
      modifier = Modifier
        .fillMaxWidth()
        .semantics { heading() },
    )

    Spacer(modifier = Modifier.height(MeTheme.spacing.xs))

    AppText(
      text = subtitle,
      textType = TextType.Body,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(MeTheme.spacing.x2l))

    AppGifImage(
      id = R.raw.baby_app_loader,
      modifier = Modifier.size(LoaderSize),
      contentDescription = BabyScaleSetupStrings.accSearchingLoader,
    )

  }
}

@PreviewTheme
@Composable
private fun PreviewBabyScaleLoader() {
  MeAppTheme {
    BabyScaleLoader(
      title = "Turn on your Scale",
      subtitle = "Place your scale on a flat, hard surface and step on it to wake it up.",
    )
  }
}
