package com.greatergoods.meapp.features.common.components

import AppHorizontalPager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.theme.MeAppTheme
import kotlinx.coroutines.launch

@Composable
fun <T> HorizontalPagerWithBottomNavigation(
    steps: List<T>,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Transparent,
    leadingContent: @Composable () -> Unit,
    middleContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable () -> Unit,
    pageContent: @Composable (T) -> Unit,
) {
    PagerBottomAppBar(
        modifier = modifier,
        containerColor = containerColor,
        leadingContent = leadingContent,
        middleContent = { middleContent?.invoke() },
        trailingContent = trailingContent,
        content = { innerModifier ->
                AppHorizontalPager(
                    steps = steps,
                    pagerState = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { item ->
                    pageContent(item)
                }
        }
    )
}

@PreviewTheme
@Composable
fun PagerWithBottomNavigationPreview() {
    MeAppTheme {
        data class PageContent(val title: String, val description: String, val pageNumber: Int)
        val pages = listOf(
            PageContent("Intro", "Welcome to the app", 1),
            PageContent("Features", "Cool stuff inside", 2),
            PageContent("Done", "You're ready to go!", 3)
        )

        val pagerState = rememberPagerState { pages.size }
        val coroutineScope = rememberCoroutineScope()

        val currentPage = pagerState.currentPage
        val isFirstPage = currentPage == 0
        val isLastPage = currentPage == pages.lastIndex

        HorizontalPagerWithBottomNavigation(
            steps = pages,
            pagerState = pagerState,
            leadingContent = {
                if (!isFirstPage) {
                    AppButton(type = ButtonType.TextPrimary, label = "BACK", size = ButtonSize.Small) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(currentPage - 1)
                        }
                    }
                }
            },
            middleContent = {
                if (!isLastPage) {
                    AppButton(type = ButtonType.TextTertiary, label = "SKIP",size = ButtonSize.Small) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pages.lastIndex)
                        }
                    }
                }
            },
            trailingContent = {
                AppButton(
                    type = ButtonType.PrimaryFilled,
                    label = if (isLastPage) "DONE" else "NEXT",
                    size = ButtonSize.Small
                ) {
                    coroutineScope.launch {
                        if (!isLastPage) {
                            pagerState.animateScrollToPage(currentPage + 1)
                        } else {
                            println("Completed onboarding!")
                        }
                    }
                }
            },
            pageContent = { page ->
                AppStyledCard(
                    cardAlignmentType = CardAlignmentType.TopCenter,
                    modifier = Modifier.padding(top = 32.dp)
                ) {
                    AppText("Title-one", TextType.Title)
                    AppText("Subtitle", TextType.Subtitle)
                    AppText(
                        "If you have trouble setting up your scale, connect with our team by tapping the help button in the top right.",
                        TextType.Body,
                    )
                }
            }
        )
    }
}

