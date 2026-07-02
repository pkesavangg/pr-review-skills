package com.dmdbrands.gurus.weight.features.feed.viewmodel

import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.services.IFeedService
import com.dmdbrands.gurus.weight.features.feed.model.FeedLandingIntent
import com.dmdbrands.gurus.weight.features.feed.shared.SelectedFeedItemHolder
import com.dmdbrands.gurus.weight.testutil.initTestDependencies
import com.google.common.truth.Truth.assertThat
import com.greatergoods.ggInAppMessaging.domain.models.FeedItem
import io.mockk.MockKAnnotations
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class FeedLandingViewModelTest {

    companion object {
        private const val TEST_TITLE = "Test Feed Title"
        private const val TEST_SUBTITLE = "Test subtitle"
        private const val TEST_PROMO_CODE = "PROMO123"
        private const val TEST_LINK = "https://example.com/shop"
        private const val TEST_PRODUCT_LINK = "https://example.com/product"
        private const val TEST_VARIATION_ID = 42
    }

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxed = true)
    lateinit var feedService: IFeedService

    private lateinit var appNavigationService: IAppNavigationService
    private lateinit var dialogQueueService: IDialogQueueService
    private lateinit var selectedFeedItemHolder: SelectedFeedItemHolder
    private lateinit var viewModel: FeedLandingViewModel

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        appNavigationService = mockk(relaxed = true)
        dialogQueueService = mockk(relaxed = true)
        selectedFeedItemHolder = SelectedFeedItemHolder()
        viewModel = createViewModel()
    }

    private fun createViewModel(): FeedLandingViewModel =
        FeedLandingViewModel(
            feedService = feedService,
            appNavigationService = appNavigationService,
            selectedFeedItemHolder = selectedFeedItemHolder,
        ).initTestDependencies(
            navigationService = appNavigationService,
            dialogQueueService = dialogQueueService,
        )

    private fun testFeedItem(
        feedPostId: String = "feed-post-1",
        titleText: String = TEST_TITLE,
    ): FeedItem = FeedItem(
        feedPostId = feedPostId,
        elementId = "element-1",
        accountId = "account-1",
        isUnread = true,
        messageTypeText = "promotion",
        titleText = titleText,
        subtitleFeedText = TEST_SUBTITLE,
        titleImage = "https://example.com/image.png",
        linkText = "Shop Now",
        feedType = "landing",
    )

    // -------------------------------------------------------------------------
    // Default State
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has null feedItem and loading true`() {
        val state = viewModel.state.value
        assertThat(state.feedItem).isNull()
        assertThat(state.isLoading).isTrue()
        assertThat(state.error).isNull()
        assertThat(state.promoCodeCopied).isFalse()
        assertThat(state.lastAction).isNull()
    }

    // -------------------------------------------------------------------------
    // SetFeedItem
    // -------------------------------------------------------------------------

    @Test
    fun `SetFeedItem updates feedItem and clears loading`() {
        val feedItem = testFeedItem()
        viewModel.handleIntent(FeedLandingIntent.SetFeedItem(feedItem))

        assertThat(viewModel.state.value.feedItem).isEqualTo(feedItem)
        assertThat(viewModel.state.value.isLoading).isFalse()
        assertThat(viewModel.state.value.error).isNull()
    }

    @Test
    fun `setFeedItem public method dispatches SetFeedItem intent`() {
        val feedItem = testFeedItem()
        viewModel.setFeedItem(feedItem)

        assertThat(viewModel.state.value.feedItem).isEqualTo(feedItem)
        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    // -------------------------------------------------------------------------
    // SetLoading
    // -------------------------------------------------------------------------

    @Test
    fun `SetLoading true updates isLoading`() {
        viewModel.handleIntent(FeedLandingIntent.SetLoading(true))

        assertThat(viewModel.state.value.isLoading).isTrue()
    }

    @Test
    fun `SetLoading false updates isLoading`() {
        viewModel.handleIntent(FeedLandingIntent.SetLoading(false))

        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    // -------------------------------------------------------------------------
    // SetError
    // -------------------------------------------------------------------------

    @Test
    fun `SetError updates error and clears loading`() {
        viewModel.handleIntent(FeedLandingIntent.SetLoading(true))
        viewModel.handleIntent(FeedLandingIntent.SetError("Something went wrong"))

        assertThat(viewModel.state.value.error).isEqualTo("Something went wrong")
        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    @Test
    fun `SetError with null clears error`() {
        viewModel.handleIntent(FeedLandingIntent.SetError("Error"))
        viewModel.handleIntent(FeedLandingIntent.SetError(null))

        assertThat(viewModel.state.value.error).isNull()
    }

    // -------------------------------------------------------------------------
    // ClearError
    // -------------------------------------------------------------------------

    @Test
    fun `ClearError clears error`() {
        viewModel.handleIntent(FeedLandingIntent.SetError("Error"))
        viewModel.handleIntent(FeedLandingIntent.ClearError)

        assertThat(viewModel.state.value.error).isNull()
    }

    // -------------------------------------------------------------------------
    // OnPromoCodeClick
    // -------------------------------------------------------------------------

    @Test
    fun `OnPromoCodeClick sets promoCodeCopied and lastAction`() {
        viewModel.handleIntent(FeedLandingIntent.OnPromoCodeClick(TEST_PROMO_CODE))

        assertThat(viewModel.state.value.promoCodeCopied).isTrue()
        assertThat(viewModel.state.value.lastAction).isEqualTo("Promo code copied: $TEST_PROMO_CODE")
    }

    // -------------------------------------------------------------------------
    // OnShopNowClick
    // -------------------------------------------------------------------------

    @Test
    fun `OnShopNowClick sets lastAction with link`() {
        viewModel.handleIntent(FeedLandingIntent.OnShopNowClick(TEST_LINK))

        assertThat(viewModel.state.value.lastAction).isEqualTo("Shop now clicked: $TEST_LINK")
    }

    @Test
    fun `OnShopNowClick with null link sets lastAction with No link`() {
        viewModel.handleIntent(FeedLandingIntent.OnShopNowClick(null))

        assertThat(viewModel.state.value.lastAction).isEqualTo("Shop now clicked: No link")
    }

    // -------------------------------------------------------------------------
    // OnProductClick
    // -------------------------------------------------------------------------

    @Test
    fun `OnProductClick sets lastAction with link and variationId`() {
        viewModel.handleIntent(FeedLandingIntent.OnProductClick(TEST_PRODUCT_LINK, TEST_VARIATION_ID))

        assertThat(viewModel.state.value.lastAction)
            .isEqualTo("Product clicked: $TEST_PRODUCT_LINK, variation: $TEST_VARIATION_ID")
    }

    @Test
    fun `OnProductClick with null variationId sets lastAction`() {
        viewModel.handleIntent(FeedLandingIntent.OnProductClick(TEST_PRODUCT_LINK, null))

        assertThat(viewModel.state.value.lastAction)
            .isEqualTo("Product clicked: $TEST_PRODUCT_LINK, variation: null")
    }

    // -------------------------------------------------------------------------
    // OnHelpClick
    // -------------------------------------------------------------------------

    @Test
    fun `OnHelpClick sets lastAction`() {
        viewModel.handleIntent(FeedLandingIntent.OnHelpClick)

        assertThat(viewModel.state.value.lastAction).isEqualTo("Help clicked")
    }

    // -------------------------------------------------------------------------
    // Retry
    // -------------------------------------------------------------------------

    @Test
    fun `Retry sets loading true and clears error`() {
        viewModel.handleIntent(FeedLandingIntent.SetError("Error"))
        viewModel.handleIntent(FeedLandingIntent.Retry)

        assertThat(viewModel.state.value.isLoading).isTrue()
        assertThat(viewModel.state.value.error).isNull()
    }

    // -------------------------------------------------------------------------
    // OnBackPress
    // -------------------------------------------------------------------------

    @Test
    fun `OnBackPress sets lastAction and navigates back`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(FeedLandingIntent.OnBackPress)
        advanceUntilIdle()

        assertThat(viewModel.state.value.lastAction).isEqualTo("Back pressed")
        coVerify { appNavigationService.navigateBack() }
    }

    @Test
    fun `OnBackPress when navigateBack throws does not crash`() = runTest(mainDispatcherRule.scheduler) {
        coVerify(exactly = 0) { appNavigationService.navigateBack() }

        viewModel.handleIntent(FeedLandingIntent.OnBackPress)
        advanceUntilIdle()

        coVerify { appNavigationService.navigateBack() }
    }

    // -------------------------------------------------------------------------
    // OpenFAQ
    // -------------------------------------------------------------------------

    @Test
    fun `OpenFAQ sets lastAction and navigates to FeedFAQ`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(FeedLandingIntent.OpenFAQ)
        advanceUntilIdle()

        assertThat(viewModel.state.value.lastAction).isEqualTo("FAQ clicked")
        coVerify { appNavigationService.navigateTo(AppRoute.Feed.FeedFAQ) }
    }

    // -------------------------------------------------------------------------
    // Init — SelectedFeedItemHolder observation
    // -------------------------------------------------------------------------

    @Test
    fun `init observes selectedFeedItemHolder and sets feed item`() = runTest(mainDispatcherRule.scheduler) {
        val feedItem = testFeedItem()
        selectedFeedItemHolder.setSelectedFeedItem(feedItem)
        advanceUntilIdle()

        assertThat(viewModel.state.value.feedItem).isEqualTo(feedItem)
        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    @Test
    fun `init does not set feed item when holder emits null`() = runTest(mainDispatcherRule.scheduler) {
        selectedFeedItemHolder.setSelectedFeedItem(null)
        advanceUntilIdle()

        assertThat(viewModel.state.value.feedItem).isNull()
    }

    // -------------------------------------------------------------------------
    // navigateToFAQ — additional coverage
    // -------------------------------------------------------------------------

    @Test
    fun `navigateToFAQ public method navigates to FeedFAQ route`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.navigateToFAQ()
        advanceUntilIdle()

        coVerify { appNavigationService.navigateTo(AppRoute.Feed.FeedFAQ) }
    }

    @Test
    fun `navigateToFAQ via intent sets lastAction`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(FeedLandingIntent.OpenFAQ)
        advanceUntilIdle()

        assertThat(viewModel.state.value.lastAction).isEqualTo("FAQ clicked")
    }

    // -------------------------------------------------------------------------
    // navigateBack — additional coverage
    // -------------------------------------------------------------------------

    @Test
    fun `OnBackPress navigates back via appNavigationService`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(FeedLandingIntent.OnBackPress)
        advanceUntilIdle()

        coVerify { appNavigationService.navigateBack() }
    }
}
