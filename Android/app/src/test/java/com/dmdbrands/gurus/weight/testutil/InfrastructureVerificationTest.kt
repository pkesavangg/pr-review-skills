package com.dmdbrands.gurus.weight.testutil

import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Verifies that all three shared test utilities work correctly together:
 *
 *   1. [MainDispatcherRule]         — swaps [kotlinx.coroutines.Dispatchers.Main] in tests
 *   2. [initTestDependencies]       — injects [BaseViewModel] fields without Hilt
 *   3. [TestFixtures]               — supplies canonical domain objects
 *
 * Uses a minimal inline ViewModel so no production code needs to change.
 */
class InfrastructureVerificationTest {

    // (1) MainDispatcherRule — sets Dispatchers.Main for the duration of each test
    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: CounterViewModel

    @BeforeEach
    fun setUp() {
        // (2) initTestDependencies — fills BaseViewModel's @Inject lateinit var fields via reflection
        viewModel = CounterViewModel().initTestDependencies()
    }

    // -------------------------------------------------------------------------
    // initTestDependencies
    // -------------------------------------------------------------------------

    @Test
    fun `initTestDependencies initialises BaseViewModel fields without Hilt`() {
        // If reflection injection failed, any access to navigationService etc. would throw.
        // The setUp() above succeeding already proves this, but an explicit assertion is clearer.
        assertThat(viewModel).isNotNull()
    }

    @Test
    fun `ViewModel state updates synchronously under UnconfinedTestDispatcher`() {
        viewModel.handleIntent(CounterIntent.Increment(by = 3))

        assertThat(viewModel.state.value.count).isEqualTo(3)
    }

    @Test
    fun `Multiple intents accumulate correctly`() {
        viewModel.handleIntent(CounterIntent.Increment(by = 2))
        viewModel.handleIntent(CounterIntent.Increment(by = 5))

        assertThat(viewModel.state.value.count).isEqualTo(7)
    }

    // -------------------------------------------------------------------------
    // TestFixtures — Accounts
    // -------------------------------------------------------------------------

    @Test
    fun `activeAccount is active and logged in`() {
        // (3) TestFixtures
        val account = TestFixtures.activeAccount

        assertThat(account.isActiveAccount).isTrue()
        assertThat(account.isLoggedIn).isTrue()
    }

    @Test
    fun `secondaryAccount is logged in but not the active session`() {
        val account = TestFixtures.secondaryAccount

        assertThat(account.isLoggedIn).isTrue()
        assertThat(account.isActiveAccount).isFalse()
    }

    @Test
    fun `inactiveAccount is not logged in`() {
        val account = TestFixtures.inactiveAccount

        assertThat(account.isLoggedIn).isFalse()
        assertThat(account.isActiveAccount).isFalse()
    }

    @Test
    fun `anAccount builder overrides defaults`() {
        val account = TestFixtures.anAccount(id = "custom-id", weightUnit = com.dmdbrands.gurus.weight.domain.model.common.WeightUnit.KG)

        assertThat(account.id).isEqualTo("custom-id")
        assertThat(account.weightUnit).isEqualTo(com.dmdbrands.gurus.weight.domain.model.common.WeightUnit.KG)
    }

    // -------------------------------------------------------------------------
    // TestFixtures — Entries
    // -------------------------------------------------------------------------

    @Test
    fun `weightEntry has expected weight`() {
        val entry = TestFixtures.weightEntry

        assertThat(entry.scale.scaleEntry.weight).isEqualTo(75.0)
        assertThat(entry.scale.scaleEntry.bodyFat).isNull()
    }

    @Test
    fun `bodyFatEntry carries body-composition metrics`() {
        val entry = TestFixtures.bodyFatEntry

        assertThat(entry.scale.scaleEntry.bodyFat).isEqualTo(22.5)
        assertThat(entry.scale.scaleEntry.muscleMass).isEqualTo(45.0)
    }

    @Test
    fun `bpmEntry has expected blood-pressure values`() {
        val entry = TestFixtures.bpmEntry

        assertThat(entry.bpmEntry.systolic).isEqualTo(120)
        assertThat(entry.bpmEntry.diastolic).isEqualTo(80)
        assertThat(entry.bpmEntry.pulse).isEqualTo(72)
    }

    // -------------------------------------------------------------------------
    // TestFixtures — Devices
    // -------------------------------------------------------------------------

    @Test
    fun `bleDevice has bluetooth deviceType`() {
        assertThat(TestFixtures.bleDevice.deviceType).isEqualTo("bluetooth")
    }

    @Test
    fun `wifiDevice has wifi deviceType`() {
        assertThat(TestFixtures.wifiDevice.deviceType).isEqualTo("wifi")
    }
}

// ---------------------------------------------------------------------------
// Minimal inline ViewModel — test infrastructure only, not production code
// ---------------------------------------------------------------------------

private data class CounterState(val count: Int = 0) : IReducer.State

private sealed class CounterIntent : IReducer.Intent {
    data class Increment(val by: Int = 1) : CounterIntent()
}

private class CounterReducer : IReducer<CounterState, CounterIntent> {
    override fun reduce(state: CounterState, intent: CounterIntent): CounterState? = when (intent) {
        is CounterIntent.Increment -> state.copy(count = state.count + intent.by)
    }
}

private class CounterViewModel : BaseIntentViewModel<CounterState, CounterIntent>(CounterReducer()) {
    override fun provideInitialState() = CounterState()
}
