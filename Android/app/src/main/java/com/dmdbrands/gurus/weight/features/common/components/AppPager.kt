
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.features.common.components.AppStyledCard
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.CardAlignmentType
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

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
            color = MeTheme.colorScheme.secondaryBackground
        ) {
            Column {
                // Use your HorizontalPager composable
                AppHorizontalPager(
                    steps = myPages, // Pass your list of data
                    pagerState = pagerState, // Pass the PagerState
                    modifier = Modifier.weight(1f) // Make it fill available vertical space
                ) { pageContent ->
                    // This lambda defines how each 'PageContent' item is displayed
                    AppStyledCard(
                        cardAlignmentType = CardAlignmentType.TopCenter,
                        modifier = Modifier.padding(top = MeTheme.spacing.md)
                    ) {
                        AppText("Title-one", TextType.Title)
                        AppText("Subtitle", TextType.Subtitle)
                        AppText(
                            "If you have trouble setting up your scale, connect with our team by tapping the help button in the top right.",
                            TextType.Body,
                        )
                    }
                }
            }
        }
    }
}


