package com.dmdbrands.gurus.weight.core.power.utility

import android.content.Context
import android.os.PowerManager
import com.dmdbrands.gurus.weight.core.power.interfaces.IPowerSaveModeObserver
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Unit tests for [PowerSaveModeObserver].
 *
 * The broadcast-driven change path needs the Android framework and is exercised in instrumented
 * tests; here we cover the synchronous read, the null-service fallback, and the initial emission.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PowerSaveModeObserverTest {

    private val powerManager: PowerManager = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    /** Builds an observer with [PowerManager.isPowerSaveMode] stubbed to [powerSaveOn]. */
    private fun observer(powerSaveOn: Boolean): IPowerSaveModeObserver {
        every { context.getSystemService(PowerManager::class.java) } returns powerManager
        every { powerManager.isPowerSaveMode } returns powerSaveOn
        return PowerSaveModeObserver(context)
    }

    @Test
    fun `isPowerSaveMode returns true when PowerManager reports enabled`() {
        assertThat(observer(powerSaveOn = true).isPowerSaveMode()).isTrue()
    }

    @Test
    fun `isPowerSaveMode returns false when PowerManager reports disabled`() {
        assertThat(observer(powerSaveOn = false).isPowerSaveMode()).isFalse()
    }

    @Test
    fun `isPowerSaveMode returns false when PowerManager service is unavailable`() {
        every { context.getSystemService(PowerManager::class.java) } returns null
        assertThat(PowerSaveModeObserver(context).isPowerSaveMode()).isFalse()
    }

    @Test
    fun `observe emits the current power-save state immediately`() = runTest {
        assertThat(observer(powerSaveOn = true).observe().first()).isTrue()
    }

    @Test
    fun `observe emits false immediately when power-save is off`() = runTest {
        assertThat(observer(powerSaveOn = false).observe().first()).isFalse()
    }
}
