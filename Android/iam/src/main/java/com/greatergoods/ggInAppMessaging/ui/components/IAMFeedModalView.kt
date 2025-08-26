package com.greatergoods.ggInAppMessaging.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.greatergoods.ggInAppMessaging.domain.models.*
import com.greatergoods.ggInAppMessaging.ui.FeedSettingsView
import com.greatergoods.ggInAppMessaging.ui.viewmodels.FeedModalViewModel
import com.greatergoods.ggInAppMessaging.ui.strings.FeedModalStrings

/**
 * Modal view for displaying feed items
 * Android equivalent of iOS IAMFeedModalView
 */
@Composable
fun IAMFeedModalView(
    feedItem: IAMFeedItem,
    onClose: () -> Unit,
    viewModel: FeedModalViewModel = hiltViewModel()
) {
    var showSettingsSheet by remember { mutableStateOf(false) }
    val shouldCloseModal by viewModel.shouldCloseModal.collectAsState()
    
    // Listen for dismissal request from view-model
    LaunchedEffect(shouldCloseModal) {
        if (shouldCloseModal) {
            onClose()
        }
    }
    
    // Initialize view model with feed item
    LaunchedEffect(feedItem) {
        viewModel.initialize(feedItem)
    }
    
    Dialog(onDismissRequest = onClose) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    // Header image
                    AsyncImage(
                        model = feedItem.titleImage,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(230.dp)
                            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
                        contentScale = ContentScale.Crop
                    )
                    
                    // Content
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(32.dp)
                    ) {
                        // Text content
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Message type
                            Text(
                                text = feedItem.messageTypeText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            // Title
                            Text(
                                text = feedItem.titleText,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            // Subtitle
                            feedItem.subtitleModalText?.let { subtitle ->
                                Text(
                                    text = subtitle, // TODO: Add rich text formatting
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        
                        // Action buttons
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Primary CTA button
                            if (feedItem.linkText.isNotEmpty()) {
                                Button(
                                    onClick = { viewModel.handlePrimaryCTA() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text(
                                        text = feedItem.linkText,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            
                            // Settings button
                            TextButton(
                                onClick = { showSettingsSheet = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = FeedModalStrings.MessageSettings,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
                
                // Close button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(28.dp)
                        .background(
                            MaterialTheme.colorScheme.surface,
                            CircleShape
                        )
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
    
    if (showSettingsSheet) {
        FeedSettingsView(
            onDismiss = { 
                showSettingsSheet = false
                viewModel.evaluatePopupSetting()
            }
        )
    }
}

@Preview
@Composable
private fun IAMFeedModalViewPreview() {
    MaterialTheme {
        IAMFeedModalView(
            feedItem = IAMFeedItem(
                feedPostId = "1",
                elementId = "1",
                accountId = "1",
                isUnread = true,
                messageTypeText = "LIGHTNING DEAL",
                titleText = "Kitchen Scales 40% Off",
                subtitleModalText = "Ends in 2 days! Save 40% on our best-selling kitchen scales.",
                subtitleFeedText = "Ends in 2 days!",
                titleImage = "https://example.com/image.jpg",
                linkTarget = null,
                linkText = "SHOP NOW",
                trigger = null,
                expiresAt = null,
                feedType = FeedType.LINK,
                landingPage = null
            ),
            onClose = {}
        )
    }
}