package com.dmdbrands.gurus.weight.features.common.components

import android.content.Context
import android.os.Build
import androidx.annotation.RawRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.borderRadius

private const val SDK_IMAGE_DECODER = 28

/**
 * Creates an [ImageLoader] that decodes GIFs using [ImageDecoderDecoder] on API 28+ to avoid
 * memory corruption in the legacy Movie-based [GifDecoder], and [GifDecoder] on older APIs.
 * Placeholder is shown only when load fails (via [SubcomposeAsyncImage] error slot).
 */
private fun createGifImageLoader(context: Context): ImageLoader =
  ImageLoader.Builder(context)
    .components {
      if (Build.VERSION.SDK_INT >= SDK_IMAGE_DECODER) {
        add(ImageDecoderDecoder.Factory())
      } else {
        add(GifDecoder.Factory())
      }
    }
    .build()

@Composable
fun AppGifImage(
  @RawRes id: Int,
  modifier: Modifier = Modifier,
  contentDescription: String? = null,
  crossfade: Boolean = true
) {
  val context = LocalContext.current
  val imageLoader = remember(context.applicationContext) {
    createGifImageLoader(context)
  }

  val gifUri = "android.resource://${context.packageName}/$id"
  val request = remember(gifUri, crossfade) {
    ImageRequest.Builder(context)
      .data(gifUri)
      .crossfade(crossfade)
      .build()
  }

  SubcomposeAsyncImage(
    model = request,
    imageLoader = imageLoader,
    contentDescription = contentDescription,
    modifier = modifier.clip(RoundedCornerShape(borderRadius.sm)),
    contentScale = ContentScale.FillBounds,
    onError = { state ->
      AppLog.w(
        "AppGifImage",
        "GIF load failed for raw id $id: ${state.result.throwable.message}"
      )
    },
    error = {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        Image(
          painter = painterResource(id = AppIcons.Default.Placeholder),
          contentDescription = contentDescription,
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Fit
        )
      }
    }
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
