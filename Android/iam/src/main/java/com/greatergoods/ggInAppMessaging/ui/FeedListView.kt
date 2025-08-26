package com.greatergoods.ggInAppMessaging.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.ggInAppMessaging.domain.models.IAMFeedItem
import com.greatergoods.ggInAppMessaging.ui.components.FeedRow
import com.greatergoods.ggInAppMessaging.ui.components.NavbarHeaderView
import com.greatergoods.ggInAppMessaging.ui.viewmodels.FeedListViewModel
import com.greatergoods.ggInAppMessaging.ui.strings.FeedListStrings

/**
 * A drop-in Composable that renders the current list of feed items coming
 * from GGInAppMessagingService. Host apps can embed this anywhere
 * in their UI without having to wire up view-models themselves.
 * Android equivalent of iOS FeedListView
 */
@Composable
fun FeedListView(
    onClickBack: () -> Unit = {},
    onClickRefresh: () -> Unit = {},
    viewModel: FeedListViewModel = hiltViewModel()
) {
    val feeds by viewModel.feeds.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    var showSettingsSheet by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NavbarHeaderView(
            title = FeedListStrings.Messages,
            onLeadingTap = onClickBack,
            onTrailingTap = { showSettingsSheet = true },
            canShowBorder = true
        )
        
        FeedContent(
            feeds = feeds,
            isConnected = isConnected,
            onClickRefresh = onClickRefresh,
            onFeedClick = { feedItem ->
                viewModel.updateClickStatus(feedItem)
            }
        )
    }
    
    if (showSettingsSheet) {
        FeedSettingsView(
            onDismiss = { showSettingsSheet = false }
        )
    }
}

@Composable
private fun FeedContent(
    feeds: List<IAMFeedItem>,
    isConnected: Boolean,
    onClickRefresh: () -> Unit,
    onFeedClick: (IAMFeedItem) -> Unit
) {
    when {
        !isConnected -> {
            EmptyStateView(
                title = FeedListStrings.NoNetworkTitle,
                message = FeedListStrings.NoNetworkText
            )
        }
        feeds.isEmpty() -> {
            EmptyStateView(
                title = FeedListStrings.DryOnSales,
                message = FeedListStrings.CheckBackSoon
            )
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(feeds) { feedItem ->
                    FeedRow(
                        feedItem = feedItem,
                        onClick = { onFeedClick(feedItem) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyStateView(
    title: String,
    message: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview
@Composable
private fun FeedListViewPreview() {
    MaterialTheme {
        FeedListView()
    }
}