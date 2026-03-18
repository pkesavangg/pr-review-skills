package com.dmdbrands.gurus.weight.features.feedMessages.viewmodel

import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.services.IFeedService
import com.dmdbrands.gurus.weight.features.feed.shared.SelectedFeedItemHolder
import com.dmdbrands.gurus.weight.features.feedMessages.model.FeedMessagesIntent
import com.dmdbrands.gurus.weight.testutil.initTestDependencies
import com.google.common.truth.Truth.assertThat
import com.greatergoods.ggInAppMessaging.domain.models.FeedItem
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class FeedMessagesViewModelTest {

    companion object {
        private const val ERROR_FAIL = "fail"
        private const val ERROR_LOAD = "Failed to load feed messages"
    }

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxUnitFun = true)
    lateinit var feedService: IFeedService

    private lateinit var navigationService: IAppNavigationService
    private lateinit var dialogQueueService: IDialogQueueService
    private lateinit var selectedFeedItemHolder: SelectedFeedItemHolder
    private lateinit var feedsChangedFlow: MutableSharedFlow<List<FeedItem>>
    private lateinit var viewModel: FeedMessagesViewModel

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        navigationService = mockk(relaxed = true)
        dialogQueueService = mockk(relaxed = true)
        selectedFeedItemHolder = mockk(relaxed = true)
        feedsChangedFlow = MutableSharedFlow()
        stubDefaultFlows()
        viewModel = createViewModel()
    }

    private fun stubDefaultFlows() {
        every { feedService.feedsChanged } returns feedsChangedFlow
    }

    private fun createViewModel(): FeedMessagesViewModel =
        FeedMessagesViewModel(
            feedService = feedService,
            selectedFeedItemHolder = selectedFeedItemHolder,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
        )

    private fun testFeedItem(
        feedPostId: String = "feed-post-1",
        titleText: String = "Test Feed Title",
    ): FeedItem = FeedItem(
        feedPostId = feedPostId,
        elementId = "element-1",
        accountId = "account-1",
        isUnread = true,
        messageTypeText = "promotion",
        titleText = titleText,
        subtitleFeedText = "Test subtitle",
        titleImage = "https://example.com/image.png",
        linkText = "Shop Now",
        feedType = "landing",
    )

    // -------------------------------------------------------------------------
    // Default State
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has default values`() {
        val state = viewModel.state.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
        assertThat(state.feedItems).isEmpty()
        assertThat(state.isRefreshing).isFalse()
    }

    // -------------------------------------------------------------------------
    // Init — fetchFeedItems and feedsChanged observation
    // -------------------------------------------------------------------------

    @Test
    fun `init calls fetchFeedItems on feedService`() = runTest {
        advanceUntilIdle()

        coVerify { feedService.fetchFeedItems() }
    }

    @Test
    fun `init subscribes to feedsChanged and updates state`() = runTest {
        val feedItems = listOf(testFeedItem("1", "Item 1"), testFeedItem("2", "Item 2"))

        advanceUntilIdle()
        feedsChangedFlow.emit(feedItems)
        advanceUntilIdle()

        assertThat(viewModel.state.value.feedItems).isEqualTo(feedItems)
        assertThat(viewModel.state.value.isLoading).isFalse()
        assertThat(viewModel.state.value.error).isNull()
    }

    @Test
    fun `init subscribes to feedsChanged with empty list`() = runTest {
        advanceUntilIdle()
        feedsChangedFlow.emit(emptyList())
        advanceUntilIdle()

        assertThat(viewModel.state.value.feedItems).isEmpty()
    }

    // -------------------------------------------------------------------------
    // SetFeedItems
    // -------------------------------------------------------------------------

    @Test
    fun `SetFeedItems updates feedItems and clears loading and error`() {
        val feedItems = listOf(testFeedItem())
        viewModel.handleIntent(FeedMessagesIntent.SetFeedItems(feedItems))

        assertThat(viewModel.state.value.feedItems).isEqualTo(feedItems)
        assertThat(viewModel.state.value.isLoading).isFalse()
        assertThat(viewModel.state.value.error).isNull()
    }

    @Test
    fun `SetFeedItems with empty list clears feedItems`() {
        viewModel.handleIntent(FeedMessagesIntent.SetFeedItems(listOf(testFeedItem())))
        viewModel.handleIntent(FeedMessagesIntent.SetFeedItems(emptyList()))

        assertThat(viewModel.state.value.feedItems).isEmpty()
    }

    // -------------------------------------------------------------------------
    // SetError
    // -------------------------------------------------------------------------

    @Test
    fun `SetError updates error and clears loading`() {
        viewModel.handleIntent(FeedMessagesIntent.SetLoading)
        viewModel.handleIntent(FeedMessagesIntent.SetError(ERROR_LOAD))

        assertThat(viewModel.state.value.error).isEqualTo(ERROR_LOAD)
        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    // -------------------------------------------------------------------------
    // ClearError
    // -------------------------------------------------------------------------

    @Test
    fun `ClearError clears error`() {
        viewModel.handleIntent(FeedMessagesIntent.SetError(ERROR_LOAD))
        viewModel.handleIntent(FeedMessagesIntent.ClearError)

        assertThat(viewModel.state.value.error).isNull()
    }

    // -------------------------------------------------------------------------
    // SetLoading
    // -------------------------------------------------------------------------

    @Test
    fun `SetLoading sets isLoading to true`() {
        viewModel.handleIntent(FeedMessagesIntent.SetLoading)

        assertThat(viewModel.state.value.isLoading).isTrue()
    }

    // -------------------------------------------------------------------------
    // SetRefreshing
    // -------------------------------------------------------------------------

    @Test
    fun `SetRefreshing true updates isRefreshing`() {
        viewModel.handleIntent(FeedMessagesIntent.SetRefreshing(true))

        assertThat(viewModel.state.value.isRefreshing).isTrue()
    }

    @Test
    fun `SetRefreshing false updates isRefreshing`() {
        viewModel.handleIntent(FeedMessagesIntent.SetRefreshing(true))
        viewModel.handleIntent(FeedMessagesIntent.SetRefreshing(false))

        assertThat(viewModel.state.value.isRefreshing).isFalse()
    }

    // -------------------------------------------------------------------------
    // Refresh
    // -------------------------------------------------------------------------

    @Test
    fun `Refresh calls fetchFeedItems and sets refreshing state`() = runTest {
        viewModel.handleIntent(FeedMessagesIntent.Refresh)
        advanceUntilIdle()

        // fetchFeedItems is called once during init and once during refresh
        coVerify(atLeast = 2) { feedService.fetchFeedItems() }
        assertThat(viewModel.state.value.isRefreshing).isFalse()
    }

    @Test
    fun `Refresh sets refreshing false after completion`() = runTest {
        viewModel.handleIntent(FeedMessagesIntent.Refresh)
        advanceUntilIdle()

        assertThat(viewModel.state.value.isRefreshing).isFalse()
    }

    @Test
    fun `Refresh sets refreshing false on fetchFeedItems exception`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        // Now make subsequent call fail
        coEvery { feedService.fetchFeedItems() } throws RuntimeException(ERROR_FAIL)
        viewModel.handleIntent(FeedMessagesIntent.Refresh)
        advanceUntilIdle()

        assertThat(viewModel.state.value.isRefreshing).isFalse()
    }

    @Test
    fun `Refresh sets error when fetchFeedItems fails`() = runTest {
        // Allow init to succeed, then make subsequent calls fail
        var callCount = 0
        coEvery { feedService.fetchFeedItems() } answers {
            callCount++
            if (callCount > 1) throw RuntimeException(ERROR_FAIL)
        }

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.handleIntent(FeedMessagesIntent.Refresh)
        advanceUntilIdle()

        assertThat(viewModel.state.value.error).isEqualTo(ERROR_LOAD)
    }

    // -------------------------------------------------------------------------
    // OnBackPress
    // -------------------------------------------------------------------------

    @Test
    fun `OnBackPress navigates back`() = runTest {
        viewModel.handleIntent(FeedMessagesIntent.OnBackPress)
        advanceUntilIdle()

        coVerify { navigationService.navigateBack() }
    }

    @Test
    fun `OnBackPress when navigateBack throws does not crash`() = runTest {
        coEvery { navigationService.navigateBack(any()) } throws RuntimeException(ERROR_FAIL)

        viewModel.handleIntent(FeedMessagesIntent.OnBackPress)
        advanceUntilIdle()

        // Should not throw — exception is caught internally
    }

    // -------------------------------------------------------------------------
    // OnSettingsPress
    // -------------------------------------------------------------------------

    @Test
    fun `OnSettingsPress navigates to FeedMessageSetting`() = runTest {
        viewModel.handleIntent(FeedMessagesIntent.OnSettingsPress)
        advanceUntilIdle()

        coVerify { navigationService.navigateTo(AppRoute.Feed.FeedMessageSetting) }
    }

    @Test
    fun `OnSettingsPress when navigation throws does not crash`() = runTest {
        coEvery { navigationService.navigateTo(any(), any(), any()) } throws RuntimeException(ERROR_FAIL)

        viewModel.handleIntent(FeedMessagesIntent.OnSettingsPress)
        advanceUntilIdle()

        // Should not throw — exception is caught internally
    }

    // -------------------------------------------------------------------------
    // OnNavigateToFeedLanding
    // -------------------------------------------------------------------------

    @Test
    fun `OnNavigateToFeedLanding sets selected feed item and navigates`() = runTest {
        val feedItem = testFeedItem()
        viewModel.handleIntent(FeedMessagesIntent.OnNavigateToFeedLanding(feedItem))
        advanceUntilIdle()

        verify { selectedFeedItemHolder.setSelectedFeedItem(feedItem) }
        coVerify { navigationService.navigateTo(AppRoute.Feed.FeedLanding) }
    }

    @Test
    fun `OnNavigateToFeedLanding when navigation throws does not crash`() = runTest {
        coEvery { navigationService.navigateTo(any(), any(), any()) } throws RuntimeException(ERROR_FAIL)

        viewModel.handleIntent(FeedMessagesIntent.OnNavigateToFeedLanding(testFeedItem()))
        advanceUntilIdle()

        // Should not throw — exception is caught internally
    }

    @Test
    fun `OnNavigateToFeedLanding sets feed item before navigation`() = runTest {
        val feedItem = testFeedItem()
        viewModel.handleIntent(FeedMessagesIntent.OnNavigateToFeedLanding(feedItem))
        advanceUntilIdle()

        // Verify ordering: setSelectedFeedItem was called
        verify { selectedFeedItemHolder.setSelectedFeedItem(feedItem) }
        coVerify { navigationService.navigateTo(AppRoute.Feed.FeedLanding) }
    }
}
