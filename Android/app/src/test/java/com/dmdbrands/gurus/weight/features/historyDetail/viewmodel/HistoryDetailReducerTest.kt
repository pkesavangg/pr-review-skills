package com.dmdbrands.gurus.weight.features.historyDetail.viewmodel

import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [HistoryDetailReducer].
 *
 * The reducer is a pure function — no mocking or coroutines needed.
 * Each test creates an initial state, dispatches an intent, and asserts the result.
 */
class HistoryDetailReducerTest {

    private lateinit var reducer: HistoryDetailReducer

    companion object {
        private const val TEST_MONTH = "March 2026"
        private const val TEST_MONTH_ALT = "February 2026"
        private const val TEST_ERROR = "Network failure"
        private const val TEST_ITEM_ID_A = 100L
        private const val TEST_ITEM_ID_B = 200L
        private const val TEST_ITEM_ID_C = 300L
    }

    private val itemA: ScaleEntry = mockk(relaxed = true)
    private val itemB: ScaleEntry = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        reducer = HistoryDetailReducer()
    }

    // -------------------------------------------------------------------------
    // Default state
    // -------------------------------------------------------------------------

    @Test
    fun `default HistoryDetailState has expected initial values`() {
        val state = HistoryDetailState()

        assertThat(state.isLoading).isFalse()
        assertThat(state.isMetric).isFalse()
        assertThat(state.errorMessage).isNull()
        assertThat(state.month).isEmpty()
        assertThat(state.itemsOpened).isEmpty()
        assertThat(state.historyItems).isEmpty()
    }

    // -------------------------------------------------------------------------
    // SetError
    // -------------------------------------------------------------------------

    @Test
    fun `SetError sets errorMessage and clears isLoading`() {
        val state = HistoryDetailState(isLoading = true, errorMessage = null)

        val result = reducer.reduce(state, HistoryDetailIntent.SetError(TEST_ERROR))

        assertThat(result?.errorMessage).isEqualTo(TEST_ERROR)
        assertThat(result?.isLoading).isFalse()
    }

    @Test
    fun `SetError overwrites previous error`() {
        val state = HistoryDetailState(errorMessage = "Old error", isLoading = true)

        val result = reducer.reduce(state, HistoryDetailIntent.SetError(TEST_ERROR))

        assertThat(result?.errorMessage).isEqualTo(TEST_ERROR)
    }

    // -------------------------------------------------------------------------
    // ClearError
    // -------------------------------------------------------------------------

    @Test
    fun `ClearError nullifies errorMessage`() {
        val state = HistoryDetailState(errorMessage = TEST_ERROR)

        val result = reducer.reduce(state, HistoryDetailIntent.ClearError)

        assertThat(result?.errorMessage).isNull()
    }

    @Test
    fun `ClearError preserves other fields`() {
        val state = HistoryDetailState(
            errorMessage = TEST_ERROR,
            month = TEST_MONTH,
            isLoading = false,
        )

        val result = reducer.reduce(state, HistoryDetailIntent.ClearError)

        assertThat(result?.errorMessage).isNull()
        assertThat(result?.month).isEqualTo(TEST_MONTH)
        assertThat(result?.isLoading).isFalse()
    }

    // -------------------------------------------------------------------------
    // LoadHistoryDetail
    // -------------------------------------------------------------------------

    @Test
    fun `LoadHistoryDetail sets isLoading to true`() {
        val state = HistoryDetailState(isLoading = false)

        val result = reducer.reduce(state, HistoryDetailIntent.LoadHistoryDetail(TEST_MONTH))

        assertThat(result?.isLoading).isTrue()
    }

    // -------------------------------------------------------------------------
    // SetItemsOpened
    // -------------------------------------------------------------------------

    @Test
    fun `SetItemsOpened stores ids as immutable list`() {
        val state = HistoryDetailState()
        val ids = listOf(TEST_ITEM_ID_A, TEST_ITEM_ID_B, TEST_ITEM_ID_C)

        val result = reducer.reduce(state, HistoryDetailIntent.SetItemsOpened(ids))

        assertThat(result?.itemsOpened).containsExactly(
            TEST_ITEM_ID_A, TEST_ITEM_ID_B, TEST_ITEM_ID_C,
        ).inOrder()
    }

    @Test
    fun `SetItemsOpened with empty list clears previous ids`() {
        val state = HistoryDetailState(
            itemsOpened = persistentListOf(TEST_ITEM_ID_A, TEST_ITEM_ID_B),
        )

        val result = reducer.reduce(state, HistoryDetailIntent.SetItemsOpened(emptyList()))

        assertThat(result?.itemsOpened).isEmpty()
    }

    // -------------------------------------------------------------------------
    // SetHistoryItems
    // -------------------------------------------------------------------------

    @Test
    fun `SetHistoryItems stores items, sets month, and clears loading and error`() {
        val state = HistoryDetailState(isLoading = true, errorMessage = "stale error")

        val result = reducer.reduce(
            state,
            HistoryDetailIntent.SetHistoryItems(
                month = TEST_MONTH,
                items = listOf(itemA, itemB),
            ),
        )

        assertThat(result?.historyItems).containsExactly(itemA, itemB).inOrder()
        assertThat(result?.month).isEqualTo(TEST_MONTH)
        assertThat(result?.isLoading).isFalse()
        assertThat(result?.errorMessage).isNull()
    }

    @Test
    fun `SetHistoryItems with empty list clears previous items`() {
        val state = HistoryDetailState(
            historyItems = persistentListOf(itemA),
            month = TEST_MONTH,
        )

        val result = reducer.reduce(
            state,
            HistoryDetailIntent.SetHistoryItems(month = TEST_MONTH_ALT, items = emptyList()),
        )

        assertThat(result?.historyItems).isEmpty()
        assertThat(result?.month).isEqualTo(TEST_MONTH_ALT)
    }

    // -------------------------------------------------------------------------
    // SetRefreshing
    // -------------------------------------------------------------------------

    @Test
    fun `SetRefreshing true sets isLoading to true`() {
        val state = HistoryDetailState(isLoading = false)

        val result = reducer.reduce(state, HistoryDetailIntent.SetRefreshing(true))

        assertThat(result?.isLoading).isTrue()
    }

    @Test
    fun `SetRefreshing false sets isLoading to false`() {
        val state = HistoryDetailState(isLoading = true)

        val result = reducer.reduce(state, HistoryDetailIntent.SetRefreshing(false))

        assertThat(result?.isLoading).isFalse()
    }

    // -------------------------------------------------------------------------
    // Retry
    // -------------------------------------------------------------------------

    @Test
    fun `Retry sets isLoading to true without clearing existing items`() {
        val state = HistoryDetailState(
            historyItems = persistentListOf(itemA),
            isLoading = false,
        )

        val result = reducer.reduce(state, HistoryDetailIntent.Retry)

        assertThat(result?.isLoading).isTrue()
        assertThat(result?.historyItems).containsExactly(itemA)
    }

    // -------------------------------------------------------------------------
    // Side-effect intents fall through to else -> state
    // -------------------------------------------------------------------------

    @Test
    fun `Refresh returns state unchanged`() {
        val state = HistoryDetailState(
            isLoading = false,
            errorMessage = "x",
            historyItems = persistentListOf(itemA),
        )

        val result = reducer.reduce(state, HistoryDetailIntent.Refresh)

        assertThat(result).isEqualTo(state)
    }

    @Test
    fun `DeleteEntry returns state unchanged by reducer`() {
        val state = HistoryDetailState()

        val result = reducer.reduce(state, HistoryDetailIntent.DeleteEntry(itemA))

        assertThat(result).isEqualTo(state)
    }

    // -------------------------------------------------------------------------
    // SetMetric
    // -------------------------------------------------------------------------

    @Test
    fun `SetMetric true sets isMetric to true`() {
        val state = HistoryDetailState(isMetric = false)

        val result = reducer.reduce(state, HistoryDetailIntent.SetMetric(true))

        assertThat(result?.isMetric).isTrue()
    }

    @Test
    fun `SetMetric false sets isMetric to false`() {
        val state = HistoryDetailState(isMetric = true)

        val result = reducer.reduce(state, HistoryDetailIntent.SetMetric(false))

        assertThat(result?.isMetric).isFalse()
    }

    @Test
    fun `SetMetric preserves other fields`() {
        val state = HistoryDetailState(
            isMetric = false,
            month = TEST_MONTH,
            isLoading = true,
        )

        val result = reducer.reduce(state, HistoryDetailIntent.SetMetric(true))

        assertThat(result?.isMetric).isTrue()
        assertThat(result?.month).isEqualTo(TEST_MONTH)
        assertThat(result?.isLoading).isTrue()
    }
}
