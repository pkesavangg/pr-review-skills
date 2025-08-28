package com.greatergoods.ggInAppMessaging.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.greatergoods.ggInAppMessaging.domain.constants.FeedStrings
import com.greatergoods.ggInAppMessaging.domain.models.FeedItem
import com.greatergoods.ggInAppMessaging.domain.models.FeedTypes

/**
 * Feed item component for displaying individual feed items
 * Android equivalent of Angular feed-item component
 */
@Composable
fun FeedItemComponent(
    feedItem: FeedItem,
    onItemClick: (FeedItem) -> Unit = {},
    onShopNowClick: (FeedItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onItemClick(feedItem) },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with image and title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Feed image
                AsyncImage(
                    model = feedItem.titleImage,
                    contentDescription = "Feed image",
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Title and subtitle
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = feedItem.titleText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = feedItem.subtitleFeedText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Unread indicator
                if (feedItem.isUnread) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(6.dp)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Link button
                if (feedItem.feedType == FeedTypes.LINK && !feedItem.linkText.isNullOrEmpty()) {
                    TextButton(
                        onClick = { onItemClick(feedItem) }
                    ) {
                        Text(
                            text = feedItem.linkText,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Shop now button for landing page feeds
                if (feedItem.feedType == FeedTypes.LANDING) {
                    Button(
                        onClick = { onShopNowClick(feedItem) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = FeedStrings.Feeds.FeedLandingPage.SHOP,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FeedItemComponentPreview() {
    val sampleFeedItem = FeedItem(
      feedPostId = "sample-1",
      elementId = "element-1",
      accountId = "account-1",
      isUnread = true,
      messageTypeText = "Promotion",
      titleText = "Special Offer",
      subtitleFeedText = "Get 20% off on selected items",
      titleImage = "https://example.com/image.jpg",
      feedType = FeedTypes.LANDING,
      linkText = "hello",
    )

    MaterialTheme {
        FeedItemComponent(
            feedItem = sampleFeedItem,
            modifier = Modifier.padding(16.dp)
        )
    }
}
