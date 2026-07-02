package com.greatergoods.ggInAppMessaging.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import androidx.compose.ui.res.painterResource
import com.greatergoods.ggInAppMessaging.domain.models.FeedItem
import com.greatergoods.ggInAppMessaging.features.common.IAMText
import com.greatergoods.ggInAppMessaging.features.common.TextType
import com.greatergoods.ggInAppMessaging.features.resources.AppIcons
import com.greatergoods.ggInAppMessaging.theme.IamTheme
import kotlinx.coroutines.launch

/**
 * Reusable composable for featured product variations (Third part)
 * Displays supporting images in a horizontal carousel with pagination dots
 * If single image, shows full container image
 * If multiple images, shows horizontal carousel with magnified current image
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeaturedProductVariations(
  feedItem: FeedItem,
  onImageClick: (String) -> Unit = {},
  modifier: Modifier = Modifier
) {
  val supportingImages = feedItem.landingPage?.supportingImage ?: emptyList()

  if (supportingImages.isEmpty()) return

  Column(
    modifier = modifier
      .fillMaxWidth()
      .then(
        if (supportingImages.size > 1) {
          Modifier.padding(horizontal = 16.dp)
          // 1.6 aspect ratio like Swift
        } else {
          Modifier
        },
      ),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // Supporting title and description
    SupportingTextSection(feedItem = feedItem)

    // Image slideshow
    if (supportingImages.size == 1) {
      // Single image - full container image with 1.6 aspect ratio (like Swift)
      SingleSupportingImage(
        imageUrl = supportingImages.first(),
        title = feedItem.titleText,
        onImageClick = onImageClick,
      )
    } else {
      // Multiple images - horizontal carousel with magnified current image
      SupportingImageCarousel(
        images = supportingImages,
        title = feedItem.titleText,
        onImageClick = onImageClick,
      )
    }
  }
}

/**
 * Supporting text section with title and description
 */
@Composable
private fun SupportingTextSection(
  feedItem: FeedItem
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
  ) {
    // Supporting title
    feedItem.landingPage?.supportingTitleText?.let { title ->
      IAMText(
        text = title,
        textType = TextType.Subtitle2,
        textAlign = TextAlign.Center,
        enableRichText = true
      )
    }

    // Supporting description
    feedItem.landingPage?.supportingDescriptionText?.let { description ->
      IAMText(
        text = description,
        textType = TextType.Body,
        textAlign = TextAlign.Center,
        enableRichText = true
      )
    }
  }
}

/**
 * Single supporting image display with 1.6 aspect ratio (matching Swift implementation)
 */
@Composable
private fun SingleSupportingImage(
  imageUrl: String,
  title: String,
  onImageClick: (String) -> Unit = {}
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onImageClick(imageUrl) },
  ) {
    AsyncImage(
      model = imageUrl,
      contentDescription = title,
      modifier = Modifier.fillMaxWidth(),
      contentScale = ContentScale.Crop,
      placeholder = painterResource(id = AppIcons.Iam.placeholderImage),
      error = painterResource(id = AppIcons.Iam.placeholderImage),
    )
  }
}

/**
 * Horizontal carousel for multiple supporting images with magnified current image
 * Uses ring buffer technique for seamless infinite scrolling without glitches
 */
@Composable
private fun SupportingImageCarousel(
  images: List<String>,
  title: String,
  onImageClick: (String) -> Unit = {}
) {
  val ringCopies = 200 // Number of times to repeat the images (like Swift implementation)
  val ringCount = images.size * ringCopies
  val ringStart = ringCount / 2 // Start in the middle of the ring
  val threshold = 50 // Threshold for re-centering
  val coroutineScope = rememberCoroutineScope()

  // Create ring buffer with repeated images
  val ringImages = remember(images) {
    if (images.size <= 1) images else {
      (0 until ringCount).map { i ->
        images[i % images.size]
      }
    }
  }

  val pagerState = rememberPagerState(
    initialPage = if (images.size <= 1) 0 else ringStart,
    pageCount = { ringImages.size },
  )

  // Ring buffer re-centering logic (prevents glitches)
  LaunchedEffect(pagerState.currentPage) {
    if (images.size > 1) {
      val currentPage = pagerState.currentPage

      // Check if we're too close to the beginning or end
      if (currentPage < threshold || currentPage > ringCount - threshold) {
        // Calculate the real index we should be showing
        val realIndex = currentPage % images.size
        // Jump back to the middle with the same real image
        val newPage = ringStart + realIndex

        // Disable animations for seamless jump
        pagerState.scrollToPage(newPage)
      }
    }
  }

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    SupportingImagePager(
      pagerState = pagerState,
      ringImages = ringImages,
      images = images,
      title = title,
      onImageClick = onImageClick,
    )

    SupportingImageDots(
      pagerState = pagerState,
      images = images,
      ringStart = ringStart,
      coroutineScope = coroutineScope,
    )
  }
}

/**
 * Horizontal pager that magnifies the current image
 */
@Composable
private fun SupportingImagePager(
  pagerState: androidx.compose.foundation.pager.PagerState,
  ringImages: List<String>,
  images: List<String>,
  title: String,
  onImageClick: (String) -> Unit,
) {
  // Image pager - shows adjacent pages slightly with 16dp spacing
  HorizontalPager(
    state = pagerState,
    modifier = Modifier.fillMaxWidth(),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 32.dp),
    beyondViewportPageCount = 2, // Pre-render 2 pages before and after visible pages
  ) { page ->
    val imageUrl = ringImages[page]

    // Calculate real page index for magnification detection
    val realPageIndex = if (images.size <= 1) page else page % images.size
    val currentRealPage = if (images.size <= 1) pagerState.currentPage else pagerState.currentPage % images.size
    val isCurrentImage = realPageIndex == currentRealPage

    // Animated scale for current image magnification
    val scale by animateFloatAsState(
      targetValue = if (isCurrentImage) 1.1f else 0.9f,
      animationSpec = tween(durationMillis = 300),
      label = "imageScale",
    )

    Box(
      modifier = Modifier
        .aspectRatio(1.6f) // 1.6 aspect ratio like Swift
        .padding(horizontal = 4.dp) // 4dp spacing between items
        .scale(scale)
        .clip(RoundedCornerShape(8.dp))
        .clickable { onImageClick(imageUrl) },
    ) {
      AsyncImage(
        model = imageUrl,
        contentDescription = title,
        modifier = Modifier.fillMaxWidth(),
        contentScale = ContentScale.Crop,
        placeholder = painterResource(id = AppIcons.Iam.placeholderImage),
        error = painterResource(id = AppIcons.Iam.placeholderImage),
      )
    }
  }
}

/**
 * Pagination dots whose active dot follows the current real page
 */
@Composable
private fun SupportingImageDots(
  pagerState: androidx.compose.foundation.pager.PagerState,
  images: List<String>,
  ringStart: Int,
  coroutineScope: kotlinx.coroutines.CoroutineScope,
) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.padding(top = 16.dp)
  ) {
    repeat(images.size) { index ->
      // Calculate current real page index from ring buffer
      val currentRealPage = if (images.size <= 1) pagerState.currentPage else pagerState.currentPage % images.size
      val isActive = index == currentRealPage

      Box(
        modifier = Modifier
          .size(7.dp)
          .clip(CircleShape)
          .background(
            if (isActive) IamTheme.colors.subSecondaryBackground else IamTheme.colors.tertiaryBackground,
          )
          .clickable {
            // Jump to the corresponding image near the middle of the ring
            if (images.size > 1) {
              val targetPage = ringStart + index
              coroutineScope.launch {
                pagerState.scrollToPage(targetPage)
              }
            }
          },
      )
    }
  }
}
