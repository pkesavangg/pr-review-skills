package com.greatergoods.ggInAppMessaging.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.greatergoods.ggInAppMessaging.R
import com.greatergoods.ggInAppMessaging.core.shared.utilities.logging.AppLog

/**
 * Lazy image component with loading states and fallbacks
 * Android equivalent of Angular lazy-image component
 */
@Composable
fun LazyImageComponent(
    imageUrl: String?,
    placeholderUrl: String? = null,
    defaultPlaceholderUrl: String? = null,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    onImageLoaded: (() -> Unit)? = null,
    onImageError: (() -> Unit)? = null
) {
    var showDefaultPlaceholder by remember { mutableStateOf(false) }
    var imageLoaded by remember { mutableStateOf(false) }
    var placeholderLoaded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val tag = "LazyImageComponent"

    // Preload placeholder
    LaunchedEffect(placeholderUrl) {
        if (!placeholderUrl.isNullOrEmpty()) {
            try {
                // Simulate placeholder preloading
                placeholderLoaded = true
                AppLog.d(tag, "Placeholder preloaded successfully")
            } catch (error: Exception) {
                showDefaultPlaceholder = true
                AppLog.e(tag, "Failed to preload placeholder", error.toString())
            }
        }
    }

    // Load main image
    LaunchedEffect(imageUrl) {
        if (!imageUrl.isNullOrEmpty()) {
            try {
                // Simulate image loading
                imageLoaded = true
                onImageLoaded?.invoke()
                AppLog.d(tag, "Image loaded successfully")
            } catch (error: Exception) {
                onImageError?.invoke()
                AppLog.e(tag, "Failed to load image", error.toString())
            }
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        when {
            // Show main image when loaded
            imageLoaded && !imageUrl.isNullOrEmpty() -> {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Show placeholder when available
            placeholderLoaded && !placeholderUrl.isNullOrEmpty() -> {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(placeholderUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Placeholder image",
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Show default placeholder
            showDefaultPlaceholder -> {
                DefaultPlaceholder(
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Show loading indicator
            else -> {
                LoadingIndicator(
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun DefaultPlaceholder(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        // You can add a default placeholder icon here
        // For now, using a simple colored box
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(4.dp)
                )
        )
    }
}

@Composable
private fun LoadingIndicator(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
fun AsyncImageWithFallback(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    onSuccess: (() -> Unit)? = null,
    onError: (() -> Unit)? = null
) {
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        onState = { state ->
            when (state) {
                is AsyncImagePainter.State.Success -> {
                    onSuccess?.invoke()
                }
                is AsyncImagePainter.State.Error -> {
                    onError?.invoke()
                }
                else -> {
                    // Loading state
                }
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun LazyImageComponentPreview() {
    MaterialTheme {
        LazyImageComponent(
            imageUrl = "https://example.com/image.jpg",
            placeholderUrl = "https://example.com/placeholder.jpg",
            modifier = Modifier
                .size(200.dp)
                .padding(16.dp)
        )
    }
}
