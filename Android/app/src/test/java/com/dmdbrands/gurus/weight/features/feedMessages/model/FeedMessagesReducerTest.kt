package com.dmdbrands.gurus.weight.features.feedMessages.model

import com.greatergoods.ggInAppMessaging.domain.models.FeedItem
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [FeedMessagesReducer].
 *
 * The reducer is a pure function — no coroutines or mocking needed.
 * The `else -> state` branch covers intents handled only by the ViewModel
 * (Initialize, OnBackPress, OnSettingsPress, OnNavigateToFeedLanding).
 */
class FeedMessagesReducerTest {

    private lateinit var reducer: FeedMessagesReducer

    private fun makeState(
        isLoading: Boolean = false,
        isRefreshing: Boolean = false,
        error: String? = null,
    ): FeedMessagesState = FeedMessagesState(
        isLoading = isLoading,
        isRefreshing = isRefreshing,
        error = error,
        feedItems = persistentListOf(),
    )

    @BeforeEach
    fun setUp() {
        reducer = FeedMessagesReducer()
    }

    // -------------------------------------------------------------------------
    // Default state
    // -------------------------------------------------------------------------

    @Test
    fun `default FeedMessagesState has expected initial values`() {
        val state = makeState()

        assertThat(state.isLoading).isFalse()
        assertThat(state.isRefreshing).isFalse()
        assertThat(state.error).isNull()
        assertThat(state.feedItems).isEmpty()
    }

    // -------------------------------------------------------------------------
    // Refresh
    // -------------------------------------------------------------------------

    @Test
    fun `Refresh sets isRefreshing to true`() {
        val state = makeState(isRefreshing = false)

        val result = reducer.reduce(state, FeedMessagesIntent.Refresh)

        assertThat(result.isRefreshing).isTrue()
    }

    @Test
    fun `Refresh when already refreshing keeps isRefreshing true`() {
        val state = makeState(isRefreshing = true)

        val result = reducer.reduce(state, FeedMessagesIntent.Refresh)

        assertThat(result.isRefreshing).isTrue()
    }

    @Test
    fun `Refresh preserves other state fields`() {
        val state = makeState(isLoading = true, error = "some error")

        val result = reducer.reduce(state, FeedMessagesIntent.Refresh)

        assertThat(result.isLoading).isTrue()
        assertThat(result.error).isEqualTo("some error")
    }

    // -------------------------------------------------------------------------
    // SetFeedItems
    // -------------------------------------------------------------------------

    @Test
    fun `SetFeedItems stores items and clears loading and error`() {
        val state = makeState(isLoading = true, error = "previous error")
        val item = mockk<FeedItem>(relaxed = true)

        val result = reducer.reduce(state, FeedMessagesIntent.SetFeedItems(listOf(item)))

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
        assertThat(result.feedItems).hasSize(1)
        assertThat(result.feedItems[0]).isSameInstanceAs(item)
    }

    @Test
    fun `SetFeedItems with empty list clears feedItems`() {
        val state = makeState(isLoading = true, error = "err")

        val result = reducer.reduce(state, FeedMessagesIntent.SetFeedItems(emptyList()))

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
        assertThat(result.feedItems).isEmpty()
    }

    @Test
    fun `SetFeedItems with multiple items stores all items`() {
        val state = makeState()
        val items = listOf(mockk<FeedItem>(relaxed = true), mockk<FeedItem>(relaxed = true))

        val result = reducer.reduce(state, FeedMessagesIntent.SetFeedItems(items))

        assertThat(result.feedItems).hasSize(2)
    }

    // -------------------------------------------------------------------------
    // SetError
    // -------------------------------------------------------------------------

    @Test
    fun `SetError sets isLoading to false and stores the error message`() {
        val state = makeState(isLoading = true, error = null)

        val result = reducer.reduce(state, FeedMessagesIntent.SetError("feed load failed"))

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isEqualTo("feed load failed")
    }

    @Test
    fun `SetError replaces an existing error`() {
        val state = makeState(error = "old error")

        val result = reducer.reduce(state, FeedMessagesIntent.SetError("new error"))

        assertThat(result.error).isEqualTo("new error")
    }

    // -------------------------------------------------------------------------
    // ClearError
    // -------------------------------------------------------------------------

    @Test
    fun `ClearError sets error to null`() {
        val state = makeState(error = "some error")

        val result = reducer.reduce(state, FeedMessagesIntent.ClearError)

        assertThat(result.error).isNull()
    }

    @Test
    fun `ClearError when error is already null leaves error null`() {
        val state = makeState(error = null)

        val result = reducer.reduce(state, FeedMessagesIntent.ClearError)

        assertThat(result.error).isNull()
    }

    @Test
    fun `ClearError preserves isLoading`() {
        val state = makeState(isLoading = true, error = "err")

        val result = reducer.reduce(state, FeedMessagesIntent.ClearError)

        assertThat(result.isLoading).isTrue()
    }

    // -------------------------------------------------------------------------
    // SetLoading
    // -------------------------------------------------------------------------

    @Test
    fun `SetLoading sets isLoading to true`() {
        val state = makeState(isLoading = false)

        val result = reducer.reduce(state, FeedMessagesIntent.SetLoading)

        assertThat(result.isLoading).isTrue()
    }

    @Test
    fun `SetLoading when already loading keeps isLoading true`() {
        val state = makeState(isLoading = true)

        val result = reducer.reduce(state, FeedMessagesIntent.SetLoading)

        assertThat(result.isLoading).isTrue()
    }

    // -------------------------------------------------------------------------
    // SetRefreshing
    // -------------------------------------------------------------------------

    @Test
    fun `SetRefreshing true sets isRefreshing to true`() {
        val state = makeState(isRefreshing = false)

        val result = reducer.reduce(state, FeedMessagesIntent.SetRefreshing(true))

        assertThat(result.isRefreshing).isTrue()
    }

    @Test
    fun `SetRefreshing false sets isRefreshing to false`() {
        val state = makeState(isRefreshing = true)

        val result = reducer.reduce(state, FeedMessagesIntent.SetRefreshing(false))

        assertThat(result.isRefreshing).isFalse()
    }

    // -------------------------------------------------------------------------
    // else branch — side-effect-only intents return state unchanged
    // -------------------------------------------------------------------------

    @Test
    fun `Initialize returns state unchanged`() {
        val state = makeState(isLoading = true, error = "err", isRefreshing = true)

        val result = reducer.reduce(state, FeedMessagesIntent.Initialize)

        assertThat(result).isEqualTo(state)
    }

    @Test
    fun `OnBackPress returns state unchanged`() {
        val state = makeState(isLoading = false, error = null)

        val result = reducer.reduce(state, FeedMessagesIntent.OnBackPress)

        assertThat(result).isEqualTo(state)
    }

    @Test
    fun `OnSettingsPress returns state unchanged`() {
        val state = makeState(isLoading = false, error = "err")

        val result = reducer.reduce(state, FeedMessagesIntent.OnSettingsPress)

        assertThat(result).isEqualTo(state)
    }

    @Test
    fun `OnNavigateToFeedLanding returns state unchanged`() {
        val state = makeState(isLoading = true, error = null)
        val item = mockk<FeedItem>(relaxed = true)

        val result = reducer.reduce(state, FeedMessagesIntent.OnNavigateToFeedLanding(item))

        assertThat(result).isEqualTo(state)
    }
}
