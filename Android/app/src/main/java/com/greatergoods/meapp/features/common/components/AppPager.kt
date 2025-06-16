
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.features.common.components.AppStyledCard
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.CardAlignmentType
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.theme.MeAppTheme


@Composable
fun <T> AppHorizontalPager(
    steps: List<T>,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    HorizontalPager(
        state = pagerState,
        userScrollEnabled = false,
        modifier = modifier.fillMaxSize()
    ) { page ->
        content(steps[page])
    }
}

/**
 * Preview function to demonstrate the usage of the HorizontalPager composable.
 */
@PreviewTheme
@Composable
fun AppHorizontalPagerPreview() {
    MeAppTheme {
        data class PageContent(val title: String, val description: String, val pageNumber: Int)

        // Create a list of example data for the pager
        val myPages = listOf(
            PageContent("Welcome", "This is the first page of our amazing app!", 1),
            PageContent("Features", "Discover all the cool features we have.", 2),
            PageContent("Settings", "Customize your experience here.", 3),
            PageContent("About Us", "Learn more about our team and mission.", 4)
        )

        // Remember the PagerState, linking it to the size of your data list
        val pagerState = rememberPagerState(pageCount = { myPages.size })

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
            ) {
                // Use your HorizontalPager composable
                AppHorizontalPager(
                    steps = myPages, // Pass your list of data
                    pagerState = pagerState, // Pass the PagerState
                    modifier = Modifier.weight(1f) // Make it fill available vertical space
                ) { pageContent -> // This lambda defines how each 'PageContent' item is displayed
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

                // Optional: Add an indicator for the current page
                Text(
                    text = "Swipe to navigate. Current Page: ${pagerState.currentPage + 1}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


