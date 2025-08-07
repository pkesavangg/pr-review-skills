package com.dmdbrands.gurus.weight.features.common.components

import AppHorizontalPager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import kotlinx.coroutines.launch

/**
 * A reusable Bottom App Bar composable function for Jetpack Compose.
 * This component provides a customizable bottom bar for navigation or actions.
 * It does NOT include a Scaffold, allowing for flexible integration.
 *
 * @param modifier The modifier to be applied to the BottomAppBar.
 * @param leadingContent A composable function for the leading content of the BottomAppBar.
 * @param middleContent A composable function for the middle content of the BottomAppBar.
 * @param trailingContent A composable function for the trailing content of the BottomAppBar.
 * @param content A composable function that defines the content of the BottomAppBar.
 */
@Composable
fun PagerBottomAppBar(
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Transparent,
    shouldCenterMiddleContent: Boolean = false,
    isBottomBarVisible: Boolean = true,
    hasMiddleContentOnly: Boolean = false,
    leadingContent: @Composable () -> Unit,
    middleContent: @Composable () -> Unit,
    trailingContent: @Composable () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    val insets = WindowInsets.ime.union(WindowInsets.navigationBars)
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = containerColor,
        bottomBar = {
          if (isBottomBarVisible) {
            BottomAppBar(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .consumeWindowInsets(insets),
              containerColor = containerColor,
              windowInsets = insets,
            ) {
              val buttonArrangement = when {
                shouldCenterMiddleContent && hasMiddleContentOnly -> Arrangement.Center
                shouldCenterMiddleContent -> Arrangement.SpaceBetween
                else -> Arrangement.Start
              }
              Row(
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .padding(MeTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = buttonArrangement,
              ) {
                leadingContent()
                if (!shouldCenterMiddleContent) {
                  Spacer(modifier = Modifier.weight(1f))
                }
                middleContent()
                if (!shouldCenterMiddleContent) {
                  Spacer(modifier = Modifier.width(MeTheme.spacing.xs))
                }
                trailingContent()
              }
            }
          }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { paddingValues ->
        Column(
            modifier = Modifier
              .padding(paddingValues)
              .then(modifier),
        ) {
            content(
                Modifier
                  .padding(
                    WindowInsets.safeDrawing
                      .only(WindowInsetsSides.Horizontal)
                      .asPaddingValues(),
                  )
                  .background(containerColor),
            )
        }
    }
}

@PreviewTheme
@Composable
fun BottomAppBarPreview() {
    MeAppTheme {
        data class PageContent(
            val title: String,
            val description: String,
            val pageNumber: Int,
        )
        // Create a list of example data for the pager
        val myPages =
            listOf(
                PageContent("Welcome", "This is the first page of our amazing app!", 1),
                PageContent("Features", "Discover all the cool features we have.", 2),
                PageContent("Settings", "Customize your experience here.", 3),
                PageContent("About Us", "Learn more about our team and mission.", 4),
            )
        // Remember the PagerState, linking it to the size of your data list
        val pagerState = rememberPagerState(pageCount = { myPages.size })
        val coroutineScope = rememberCoroutineScope() // Coroutine scope for animated scrolls
        // Logic to determine button visibility and labels
        val isFirstPage = pagerState.currentPage == 0
        val isLastPage = pagerState.currentPage == myPages.lastIndex
        val nextLabel = if (isLastPage) "DONE" else "NEXT"

        PagerBottomAppBar(
            leadingContent = {
                AppButton(type = ButtonType.TextPrimary, label = "BACK", size = ButtonSize.Small) {
                    if (!isFirstPage) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    }
                }
            },
            middleContent = {
                AppButton(type = ButtonType.TextTertiary, label = "SKIP", size = ButtonSize.Small) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(myPages.lastIndex)
                    }
                }
            },
            trailingContent = {
                AppButton(type = ButtonType.PrimaryFilled, label = nextLabel, size = ButtonSize.Small) {
                    coroutineScope.launch {
                        if (!isLastPage) {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        } else {
                            // Handle DONE action (e.g., navigate to home screen)
                            println("Finished onboarding!")
                        }
                    }
                }
            },
            content = { modifier ->
                // 'modifier' here includes the padding from Scaffold
                Surface(
                    modifier = modifier.fillMaxSize(), // Apply the passed modifier with padding
                    color = MeTheme.colorScheme.primaryBackground,
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        // Using your custom AppLinearProgressIndicator here
                        AppLinearProgressIndicator(
                            progress = (pagerState.currentPage + 1).toFloat() / myPages.size,
                        )
                        AppHorizontalPager(
                            steps = myPages, // Pass your list of data,
                            pagerState = pagerState, // Pass the PagerState
                            modifier = Modifier.weight(1f), // Make it fill available vertical space
                        ) { pageContent ->
                            // This lambda defines how each 'PageContent' item is displayed
                            AppStyledCard(
                                cardAlignmentType = CardAlignmentType.TopCenter,
                                modifier = Modifier.padding(top = 32.dp),
                            ) {
                                AppText("Title-one", TextType.Title)
                                AppText("Subtitle", TextType.Subtitle)
                                AppText(
                                    "If you have trouble setting up your scale, connect with our team by tapping the help button in the top right.",
                                    TextType.Body,
                                )
                            }
                        }

                        // Optional: Add an indicator for the current page
                        Text(
                            text = "Swipe to navigate. Current Page: ${pagerState.currentPage + 1}",
                            modifier =
                                Modifier
                                  .fillMaxWidth()
                                  .padding(16.dp),
                            style = MeTheme.typography.body3,
                            color = MeTheme.colorScheme.tertiaryAction,
                        )
                    }
                }
            },
        )
    }
}
