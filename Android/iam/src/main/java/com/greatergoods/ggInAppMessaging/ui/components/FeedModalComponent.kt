package com.greatergoods.ggInAppMessaging.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.greatergoods.ggInAppMessaging.domain.constants.FeedStrings
import com.greatergoods.ggInAppMessaging.domain.models.FeedItem
import com.greatergoods.ggInAppMessaging.domain.models.FeedTypes

/**
 * Feed modal component for displaying feed details
 * Android equivalent of Angular feed-modal component
 */
@Composable
fun FeedModalComponent(
    feedItem: FeedItem,
    onDismiss: () -> Unit = {},
    onShopNowClick: (FeedItem) -> Unit = {},
    onCopyPromoCode: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header with close button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp)
                ) {
                    Text(
                        text = feedItem.messageTypeText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Text(
                            text = "✕",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Content
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Title image
                    AsyncImage(
                        model = feedItem.titleImage,
                        contentDescription = "Feed title image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Title
                    Text(
                        text = feedItem.titleText,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Subtitle (modal specific)
                    feedItem.subtitleModalText?.let { subtitle ->
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Landing page content
                    feedItem.landingPage?.let { landingPage ->
                        LandingPageContent(
                            landingPage = landingPage,
                            onShopNowClick = { onShopNowClick(feedItem) },
                            onCopyPromoCode = onCopyPromoCode
                        )
                    }

                    // Link content
                    if (feedItem.feedType == FeedTypes.LINK && !feedItem.linkText.isNullOrEmpty()) {
                        LinkContent(
                            feedItem = feedItem,
                            onShopNowClick = { onShopNowClick(feedItem) }
                        )
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Secondary action (if available)
                    if (feedItem.feedType == FeedTypes.LANDING) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Close")
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Button(
                            onClick = { onShopNowClick(feedItem) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(FeedStrings.Feeds.FeedLandingPage.SHOP)
                        }
                    } else {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LandingPageContent(
    landingPage: com.greatergoods.ggInAppMessaging.domain.models.LandingPage,
    onShopNowClick: () -> Unit,
    onCopyPromoCode: (String) -> Unit
) {
    Column {
        // Promo code section
        landingPage.promoCode?.let { promoCode ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "${FeedStrings.Feeds.FeedLandingPage.Promotion.MESSAGE_PREFIX}${FeedStrings.Feeds.FeedLandingPage.Promotion.CODE}${FeedStrings.Feeds.FeedLandingPage.Promotion.MESSAGE_SUFFIX}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = promoCode,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = { onCopyPromoCode(promoCode) }
                        ) {
                            Text(
                                text = FeedStrings.Feeds.FeedLandingPage.COPY,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Featured product section
        landingPage.featuredProduct?.let { products ->
            if (products.isNotEmpty()) {
                Text(
                    text = "Featured Products",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                products.forEach { product ->
                    FeaturedProductItem(product = product)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun FeaturedProductItem(
    product: com.greatergoods.ggInAppMessaging.domain.models.FeaturedProduct
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = product.productImage,
                contentDescription = "Product image",
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = product.titleText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = product.linkText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LinkContent(
    feedItem: FeedItem,
    onShopNowClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = feedItem.linkText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onShopNowClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Link")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FeedModalComponentPreview() {
    val sampleFeedItem = FeedItem(
        feedPostId = "sample-1",
        elementId = "element-1",
        accountId = "account-1",
        isUnread = true,
        messageTypeText = "Promotion",
        titleText = "Special Offer",
        subtitleModalText = "Get exclusive deals on our products",
        subtitleFeedText = "Get 20% off on selected items",
        titleImage = "https://example.com/image.jpg",
        feedType = FeedTypes.LANDING,
        landingPage = com.greatergoods.ggInAppMessaging.domain.models.LandingPage(
            feedLandingPageId = "landing-1",
            feedPostId = "sample-1",
            titleText = "Special Offer",
            promoCode = "SAVE20"
        )
    )

    MaterialTheme {
        FeedModalComponent(
            feedItem = sampleFeedItem
        )
    }
}
