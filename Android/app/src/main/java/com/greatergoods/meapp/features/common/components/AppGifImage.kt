package com.greatergoods.meapp.features.common.components

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.request.ImageRequest
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme.borderRadius

@Composable
fun AppGifImage(
  @RawRes id: Int,
  modifier: Modifier = Modifier,
  contentDescription: String? = null,
  crossfade: Boolean = true
) {
  val context = LocalContext.current

  val imageLoader = ImageLoader.Builder(context)
    .components {
      add(GifDecoder.Factory())
    }
    .build()

  val gifUri = "android.resource://${context.packageName}/$id"

  AsyncImage(
    model = ImageRequest.Builder(context)
      .data(gifUri)
      .crossfade(crossfade)
      .build(),
    imageLoader = imageLoader,
    contentDescription = contentDescription,
    modifier = modifier.clip(RoundedCornerShape(borderRadius.sm)),
    contentScale = ContentScale.FillBounds
  )
}

@PreviewTheme
@Composable
fun AppGifImagePreview() {
  MeAppTheme {
    Column(modifier = Modifier.fillMaxSize(),
           horizontalAlignment = Alignment.CenterHorizontally) {
      AppGifImage(
        id = AppIcons.Setup.StepOnGif,   // your gif file in res/raw
        modifier = Modifier.clip(RoundedCornerShape(borderRadius.sm)),
        contentDescription = "Loading animation"
      )
    }
  }
}
