package com.dmdbrands.gurus.weight.features.help.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [HelpReducer].
 *
 * The reducer is a pure function — no mocking or coroutines needed.
 * Each test creates an initial state, dispatches an intent, and asserts the result.
 */
class HelpReducerTest {

    private lateinit var reducer: HelpReducer

    @BeforeEach
    fun setUp() {
        reducer = HelpReducer()
    }

    // -------------------------------------------------------------------------
    // Default state
    // -------------------------------------------------------------------------

    @Test
    fun `default HelpState has expected initial values`() {
        val state = HelpState()

        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
    }

    // -------------------------------------------------------------------------
    // ShowModelNumberHelpPopup
    // -------------------------------------------------------------------------

    @Test
    fun `ShowModelNumberHelpPopup clears isLoading`() {
        val state = HelpState(isLoading = true)

        val result = reducer.reduce(state, HelpIntent.ShowModelNumberHelpPopup)

        assertThat(result.isLoading).isFalse()
    }

    @Test
    fun `ShowModelNumberHelpPopup clears error`() {
        val state = HelpState(error = "some error")

        val result = reducer.reduce(state, HelpIntent.ShowModelNumberHelpPopup)

        assertThat(result.error).isNull()
    }

    @Test
    fun `ShowModelNumberHelpPopup on default state returns unchanged values`() {
        val state = HelpState()

        val result = reducer.reduce(state, HelpIntent.ShowModelNumberHelpPopup)

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
    }

    // -------------------------------------------------------------------------
    // OnBack
    // -------------------------------------------------------------------------

    @Test
    fun `OnBack clears isLoading`() {
        val state = HelpState(isLoading = true)

        val result = reducer.reduce(state, HelpIntent.OnBack)

        assertThat(result.isLoading).isFalse()
    }

    @Test
    fun `OnBack clears error`() {
        val state = HelpState(error = "navigation error")

        val result = reducer.reduce(state, HelpIntent.OnBack)

        assertThat(result.error).isNull()
    }

    @Test
    fun `OnBack on default state returns unchanged values`() {
        val state = HelpState()

        val result = reducer.reduce(state, HelpIntent.OnBack)

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
    }

    // -------------------------------------------------------------------------
    // OpenDebugMenu
    // -------------------------------------------------------------------------

    @Test
    fun `OpenDebugMenu clears isLoading`() {
        val state = HelpState(isLoading = true)

        val result = reducer.reduce(state, HelpIntent.OpenDebugMenu)

        assertThat(result.isLoading).isFalse()
    }

    @Test
    fun `OpenDebugMenu clears error`() {
        val state = HelpState(error = "debug error")

        val result = reducer.reduce(state, HelpIntent.OpenDebugMenu)

        assertThat(result.error).isNull()
    }

    @Test
    fun `OpenDebugMenu on default state returns unchanged values`() {
        val state = HelpState()

        val result = reducer.reduce(state, HelpIntent.OpenDebugMenu)

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
    }

    // -------------------------------------------------------------------------
    // OpenUrl
    // -------------------------------------------------------------------------

    @Test
    fun `OpenUrl clears isLoading`() {
        val state = HelpState(isLoading = true)

        val result = reducer.reduce(state, HelpIntent.OpenUrl("https://example.com"))

        assertThat(result.isLoading).isFalse()
    }

    @Test
    fun `OpenUrl clears error`() {
        val state = HelpState(error = "load error")

        val result = reducer.reduce(state, HelpIntent.OpenUrl("https://weightgurus.com/help"))

        assertThat(result.error).isNull()
    }

    @Test
    fun `OpenUrl with empty url clears isLoading and error`() {
        val state = HelpState(isLoading = true, error = "error")

        val result = reducer.reduce(state, HelpIntent.OpenUrl(""))

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
    }

    // -------------------------------------------------------------------------
    // Error
    // -------------------------------------------------------------------------

    @Test
    fun `Error sets error message`() {
        val state = HelpState(error = null)

        val result = reducer.reduce(state, HelpIntent.Error("something went wrong"))

        assertThat(result.error).isEqualTo("something went wrong")
    }

    @Test
    fun `Error clears isLoading`() {
        val state = HelpState(isLoading = true)

        val result = reducer.reduce(state, HelpIntent.Error("oops"))

        assertThat(result.isLoading).isFalse()
    }

    @Test
    fun `Error with empty message sets empty error`() {
        val state = HelpState()

        val result = reducer.reduce(state, HelpIntent.Error(""))

        assertThat(result.error).isEqualTo("")
    }

    @Test
    fun `Error overwrites previous error message`() {
        val state = HelpState(error = "old error")

        val result = reducer.reduce(state, HelpIntent.Error("new error"))

        assertThat(result.error).isEqualTo("new error")
    }
}
