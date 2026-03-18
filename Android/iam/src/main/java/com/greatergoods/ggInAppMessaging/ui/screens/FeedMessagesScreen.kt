package com.greatergoods.ggInAppMessaging.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.ggInAppMessaging.domain.models.FeedTypes
import com.greatergoods.ggInAppMessaging.features.common.AppIcon
import com.greatergoods.ggInAppMessaging.features.common.AppIconType
import com.greatergoods.ggInAppMessaging.features.common.IAMText
import com.greatergoods.ggInAppMessaging.features.common.TextType
import com.greatergoods.ggInAppMessaging.features.resources.AppIcons
import com.greatergoods.ggInAppMessaging.theme.IamTheme
import com.greatergoods.ggInAppMessaging.ui.components.FeedItemCard
import com.greatergoods.ggInAppMessaging.ui.viewmodel.FeedMessagesIntent
import com.greatergoods.ggInAppMessaging.ui.viewmodel.FeedMessagesViewModel
import com.greatergoods.ggInAppMessaging.util.LinkOpener

/**
 * Main Feed Messages Screen Content
 * Displays deals and messages with empty state handling
 * Note: Top navigation bar is provided by the app
 */
@Composable
fun FeedMessagesScreen(
    onSettingsPress: () -> Unit,
    onNavigateToFeedLanding: (com.greatergoods.ggInAppMessaging.domain.models.FeedItem) -> Unit = { _ -> },
    modifier: Modifier = Modifier,
) {
    // Create ViewModel using Hilt
    val viewModel: FeedMessagesViewModel = hiltViewModel()
    val context = LocalContext.current

    // Global composable approach - automatically recomposes when colors change anywhere
    val colors = IamTheme.colors
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Load feed items on first composition
    LaunchedEffect(Unit) {
        viewModel.handleIntent(FeedMessagesIntent.LoadFeedItems)
    }


    Column(
        modifier = modifier
          .fillMaxSize()
          .background(colors.secondaryBackground)
    ) {
        // Section Header
        SectionHeader(
            title = "Deals on Goods",
            onSettingsClick = {
                viewModel.handleIntent(FeedMessagesIntent.OnSettingsClick)
                onSettingsPress()
            },
        )

        //Content based on state
        when {
            state.showEmptyState -> {
                // Empty State Content
                EmptyStateContent()
            }


            else -> {
              val listState = rememberLazyListState()
                // Feed Items List
                LazyColumn(
                  state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.feedItems) { feedItem ->
                        FeedItemCard(
                            feedItem = feedItem,
                            onItemClick = { item ->
                                viewModel.handleIntent(FeedMessagesIntent.OnFeedItemClick(item.elementId))
                                when (item.feedType) {
                                    FeedTypes.LINK -> {
                                        LinkOpener.openInCustomTab(
                                            context = context,
                                            url = item.linkTarget,
                                            showTitle = true,
                                        )
                                    }
                                    FeedTypes.LANDING -> {
                                        onNavigateToFeedLanding(item)
                                    }
                                    else -> {
                                        // Handle unknown feed types or do nothing
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    onSettingsClick: () -> Unit,
) {

    Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        AppIcon(
            id = AppIcons.Logo,
            contentDescription = "Deals Logo",
            tintColor = IamTheme.colors.textHeading,
            type = AppIconType.Tertiary,
            onClick = { /* action */ },
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        IAMText(
          textType = TextType.Title,
          text = title,
        )
      Spacer(modifier = Modifier.weight(1f)) // Pushes the next item to the end
        AppIcon(
            id = AppIcons.Settings, // Using system settings icon
            contentDescription = "Settings",
            type = AppIconType.Primary, // Will automatically use colors.brandWgPrimary
            onClick = onSettingsClick,
            modifier = Modifier.size(24.dp).align(Alignment.CenterVertically),
        )
    }
}

@Composable
private fun EmptyStateContent() {
    // Use a Box with fillMaxSize to center the column vertically
        Column(
          modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
            // Primary Empty State Message
            Text(
                text = "Dry on Deals...for Now",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = IamTheme.colors.textHeading,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Secondary Message
            Text(
                text = "Check back soon",
                style = MaterialTheme.typography.bodyLarge,
                color = IamTheme.colors.textBody,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
}

// Preview removed to avoid build issues - can be added later when needed
