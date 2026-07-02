package com.dmdbrands.gurus.weight.features.history.viewmodel

import com.dmdbrands.gurus.weight.domain.model.common.BabyWeekGroup
import com.dmdbrands.gurus.weight.domain.model.common.BpHistoryMonth
import com.dmdbrands.gurus.weight.domain.model.common.HistoryMonth
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [HistoryReducer].
 *
 * HistoryReducer holds list-based state and always returns a new [HistoryState]
 * (never null), which makes it a good contrast to SettingsReducer.
 *
 * This file demonstrates two patterns:
 *   1. Pure state assertions — no mocks needed for simple data classes
 *   2. Using mockk() to create a model object whose internals we don't care about
 */
class HistoryReducerTest {

    private lateinit var reducer: HistoryReducer

    // mockk() creates a lenient mock of HistoryMonth — we don't need its fields,
    // only to verify the reducer puts the right items into state.
    private val itemA: HistoryMonth = mockk(relaxed = true)
    private val itemB: HistoryMonth = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        reducer = HistoryReducer()
    }

    // -------------------------------------------------------------------------
    // Loading
    // -------------------------------------------------------------------------

    @Test
    fun `Loading true sets isLoading to true`() {
        val state = HistoryState(isLoading = false)

        val result = reducer.reduce(state, HistoryIntent.Loading(isLoading = true))

        assertThat(result.isLoading).isTrue()
    }

    @Test
    fun `Loading false sets isLoading to false`() {
        val state = HistoryState(isLoading = true)

        val result = reducer.reduce(state, HistoryIntent.Loading(isLoading = false))

        assertThat(result.isLoading).isFalse()
    }

    // -------------------------------------------------------------------------
    // Error handling
    // -------------------------------------------------------------------------

    @Test
    fun `SetError stores the message and clears isLoading`() {
        val state = HistoryState(isLoading = true, errorMessage = null)

        val result = reducer.reduce(state, HistoryIntent.SetError("Timeout"))

        assertThat(result.errorMessage).isEqualTo("Timeout")
        assertThat(result.isLoading).isFalse()
    }

    @Test
    fun `ClearError nullifies errorMessage`() {
        val state = HistoryState(errorMessage = "Some error")

        val result = reducer.reduce(state, HistoryIntent.ClearError)

        assertThat(result.errorMessage).isNull()
    }

    // -------------------------------------------------------------------------
    // History items
    // -------------------------------------------------------------------------

    @Test
    fun `SetHistoryItems stores items and clears loading and error`() {
        val state = HistoryState(isLoading = true, errorMessage = "stale error")

        val result = reducer.reduce(state, HistoryIntent.SetHistoryItems(listOf(itemA, itemB)))

        assertThat(result.historyItems).containsExactly(itemA, itemB).inOrder()
        assertThat(result.isLoading).isFalse()
        assertThat(result.errorMessage).isNull()
    }

    @Test
    fun `SetHistoryItems with empty list clears previous items`() {
        val state = HistoryState(historyItems = persistentListOf(itemA, itemB))

        val result = reducer.reduce(state, HistoryIntent.SetHistoryItems(emptyList()))

        assertThat(result.historyItems).isEmpty()
    }

    // -------------------------------------------------------------------------
    // BP history items
    // -------------------------------------------------------------------------

    @Test
    fun `SetBpHistoryItems stores items and clears loading and error`() {
        val bpA: BpHistoryMonth = mockk(relaxed = true)
        val bpB: BpHistoryMonth = mockk(relaxed = true)
        val state = HistoryState(isLoading = true, errorMessage = "stale error")

        val result = reducer.reduce(state, HistoryIntent.SetBpHistoryItems(listOf(bpA, bpB)))

        assertThat(result.bpHistoryItems).containsExactly(bpA, bpB).inOrder()
        assertThat(result.isLoading).isFalse()
        assertThat(result.errorMessage).isNull()
    }

    @Test
    fun `SetBpHistoryItems with empty list clears previous items`() {
        val bpA: BpHistoryMonth = mockk(relaxed = true)
        val state = HistoryState(bpHistoryItems = persistentListOf(bpA))

        val result = reducer.reduce(state, HistoryIntent.SetBpHistoryItems(emptyList()))

        assertThat(result.bpHistoryItems).isEmpty()
    }

    // -------------------------------------------------------------------------
    // Baby history items
    // -------------------------------------------------------------------------

    @Test
    fun `SetBabyHistoryItems stores items and clears loading and error`() {
        val babyA: BabyWeekGroup = mockk(relaxed = true)
        val state = HistoryState(isLoading = true, errorMessage = "stale error")

        val result = reducer.reduce(state, HistoryIntent.SetBabyHistoryItems(listOf(babyA)))

        assertThat(result.babyHistoryItems).containsExactly(babyA)
        assertThat(result.isLoading).isFalse()
        assertThat(result.errorMessage).isNull()
    }

    @Test
    fun `SetBabyHistoryItems with empty list clears previous items`() {
        val babyA: BabyWeekGroup = mockk(relaxed = true)
        val state = HistoryState(babyHistoryItems = persistentListOf(babyA))

        val result = reducer.reduce(state, HistoryIntent.SetBabyHistoryItems(emptyList()))

        assertThat(result.babyHistoryItems).isEmpty()
    }

    // -------------------------------------------------------------------------
    // Retry
    // -------------------------------------------------------------------------

    @Test
    fun `Retry sets isLoading to true without clearing existing items`() {
        val state = HistoryState(historyItems = persistentListOf(itemA), isLoading = false)

        val result = reducer.reduce(state, HistoryIntent.Retry)

        assertThat(result.isLoading).isTrue()
        // Existing items are preserved so the UI can show stale data while retrying
        assertThat(result.historyItems).containsExactly(itemA)
    }

    // -------------------------------------------------------------------------
    // Unhandled intents fall through to else -> state
    // -------------------------------------------------------------------------

    @Test
    fun `Refresh returns state unchanged`() {
        val state = HistoryState(isLoading = false, errorMessage = "x", historyItems = persistentListOf(itemA))

        val result = reducer.reduce(state, HistoryIntent.Refresh)

        assertThat(result).isEqualTo(state)
    }

    // -------------------------------------------------------------------------
    // Default state sanity check
    // -------------------------------------------------------------------------

    @Test
    fun `default HistoryState has sensible initial values`() {
        val state = HistoryState()

        assertThat(state.isLoading).isFalse()
        assertThat(state.errorMessage).isNull()
        assertThat(state.historyItems).isEmpty()
    }
}
