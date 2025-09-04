package com.greatergoods.ggInAppMessaging.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.greatergoods.ggInAppMessaging.domain.models.*

/**
 * Individual feed row component
 * Android equivalent of iOS FeedRow
 */
@Composable
fun FeedRow(
    feedItem: FeedItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Feed image
            AsyncImage(
                model = feedItem.titleImage,
                contentDescription = null,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )

            // Feed content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Message type
                Text(
                    text = feedItem.messageTypeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )

                // Title
                Text(
                    text = feedItem.titleText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Subtitle
                Text(
                    text = feedItem.subtitleFeedText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Unread indicator
            if (feedItem.isUnread == true) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(50)
                        )
                        .align(Alignment.Top)
                )
            }
        }
    }
}

@Preview
@Composable
private fun FeedRowPreview() {
    MaterialTheme {
        FeedRow(
            feedItem = FeedItem(
                feedPostId = "1",
                elementId = "1",
                accountId = "1",
                isUnread = true,
                messageTypeText = "LIGHTNING DEAL",
                titleText = "Kitchen Scales 40% Off",
                subtitleModalText = null,
                subtitleFeedText = "Ends in 2 days!",
                titleImage = "https://example.com/image.jpg",
                linkTarget = null,
                linkText = "BUY NOW",
                trigger = null,
                expiresAt = null,
                feedType = "link",
                landingPage = null
            ),
            onClick = {}
        )
    }
}
