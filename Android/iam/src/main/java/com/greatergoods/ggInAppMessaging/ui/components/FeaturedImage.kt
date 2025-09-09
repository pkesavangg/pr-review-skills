package com.greatergoods.ggInAppMessaging.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.greatergoods.ggInAppMessaging.domain.models.FeedItem

/**
 * Reusable composable for the featured image section
 * Displays the main product image from the feed item
 */
@Composable
fun FeaturedImage(
  feedItem: FeedItem,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(300.dp),
    contentAlignment = Alignment.Center,
  ) {
    AsyncImage(
      model = feedItem.titleImage,
      contentDescription = feedItem.titleText,
      modifier = Modifier.fillMaxWidth(),
      contentScale = ContentScale.Crop,
    )
  }
}
