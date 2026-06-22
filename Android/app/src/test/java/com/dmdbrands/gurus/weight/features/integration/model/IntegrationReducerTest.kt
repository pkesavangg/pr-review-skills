package com.dmdbrands.gurus.weight.features.integration.model

import com.dmdbrands.gurus.weight.domain.model.api.integration.IntegrationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [IntegrationReducer].
 *
 * The reducer is a pure function — no mocking or coroutines needed.
 * Each test creates an initial state, dispatches an intent, and asserts the result.
 */
class IntegrationReducerTest {

    private lateinit var reducer: IntegrationReducer

    private val fitbitItem = IntegrationItem(
        provider = IntegrationProvider.Fitbit,
        name = "Fitbit",
        isConnected = false,
        isValid = true,
        iconRes = 0,
    )

    private val mfpItem = IntegrationItem(
        provider = IntegrationProvider.MyFitnessPal,
        name = "MyFitnessPal",
        isConnected = true,
        isValid = true,
        iconRes = 0,
    )

    private val healthConnectItem = IntegrationItem(
        provider = IntegrationProvider.HealthConnect,
        name = "Health Connect",
        isConnected = false,
        isValid = true,
        iconRes = 0,
    )

    @BeforeEach
    fun setUp() {
        reducer = IntegrationReducer()
    }

    // -------------------------------------------------------------------------
    // Default state
    // -------------------------------------------------------------------------

    @Test
    fun `default IntegrationState has expected initial values`() {
        val state = IntegrationState()

        assertThat(state.integrations).isEmpty()
        assertThat(state.selectedIntegrationForDisconnect).isNull()
    }

    // -------------------------------------------------------------------------
    // InitializeIntegrations
    // -------------------------------------------------------------------------

    @Test
    fun `InitializeIntegrations populates integrations with all providers`() {
        val state = IntegrationState()

        val result = reducer.reduce(state, IntegrationIntent.InitializeIntegrations)

        assertThat(result.integrations).hasSize(IntegrationProvider.getAllProviders().size)
    }

    @Test
    fun `InitializeIntegrations contains Fitbit provider`() {
        val state = IntegrationState()

        val result = reducer.reduce(state, IntegrationIntent.InitializeIntegrations)

        assertThat(result.integrations.map { it.provider }).contains(IntegrationProvider.Fitbit)
    }

    @Test
    fun `InitializeIntegrations contains MyFitnessPal provider`() {
        val state = IntegrationState()

        val result = reducer.reduce(state, IntegrationIntent.InitializeIntegrations)

        assertThat(result.integrations.map { it.provider }).contains(IntegrationProvider.MyFitnessPal)
    }

    @Test
    fun `InitializeIntegrations contains HealthConnect provider`() {
        val state = IntegrationState()

        val result = reducer.reduce(state, IntegrationIntent.InitializeIntegrations)

        assertThat(result.integrations.map { it.provider }).contains(IntegrationProvider.HealthConnect)
    }

    @Test
    fun `InitializeIntegrations preserves selectedIntegrationForDisconnect`() {
        val state = IntegrationState(selectedIntegrationForDisconnect = fitbitItem)

        val result = reducer.reduce(state, IntegrationIntent.InitializeIntegrations)

        assertThat(result.selectedIntegrationForDisconnect).isEqualTo(fitbitItem)
    }

    // -------------------------------------------------------------------------
    // SetIntegrations
    // -------------------------------------------------------------------------

    @Test
    fun `SetIntegrations replaces integrations list`() {
        val state = IntegrationState()

        val result = reducer.reduce(
            state,
            IntegrationIntent.SetIntegrations(listOf(fitbitItem, mfpItem)),
        )

        assertThat(result.integrations).containsExactly(fitbitItem, mfpItem).inOrder()
    }

    @Test
    fun `SetIntegrations with empty list clears integrations`() {
        val state = IntegrationState(integrations = listOf(fitbitItem).toImmutableList())

        val result = reducer.reduce(state, IntegrationIntent.SetIntegrations(emptyList()))

        assertThat(result.integrations).isEmpty()
    }

    @Test
    fun `SetIntegrations preserves selectedIntegrationForDisconnect`() {
        val state = IntegrationState(selectedIntegrationForDisconnect = mfpItem)

        val result = reducer.reduce(
            state,
            IntegrationIntent.SetIntegrations(listOf(fitbitItem)),
        )

        assertThat(result.selectedIntegrationForDisconnect).isEqualTo(mfpItem)
    }

    // -------------------------------------------------------------------------
    // RemoveIntegration
    // -------------------------------------------------------------------------

    @Test
    fun `RemoveIntegration sets selectedIntegrationForDisconnect`() {
        val state = IntegrationState(selectedIntegrationForDisconnect = null)

        val result = reducer.reduce(state, IntegrationIntent.RemoveIntegration(fitbitItem))

        assertThat(result.selectedIntegrationForDisconnect).isEqualTo(fitbitItem)
    }

    @Test
    fun `RemoveIntegration overwrites previous selectedIntegrationForDisconnect`() {
        val state = IntegrationState(selectedIntegrationForDisconnect = mfpItem)

        val result = reducer.reduce(state, IntegrationIntent.RemoveIntegration(fitbitItem))

        assertThat(result.selectedIntegrationForDisconnect).isEqualTo(fitbitItem)
    }

    @Test
    fun `RemoveIntegration preserves integrations list`() {
        val state = IntegrationState(
            integrations = listOf(fitbitItem, mfpItem).toImmutableList(),
        )

        val result = reducer.reduce(state, IntegrationIntent.RemoveIntegration(fitbitItem))

        assertThat(result.integrations).containsExactly(fitbitItem, mfpItem).inOrder()
    }

    // -------------------------------------------------------------------------
    // UpdateIntegrationConnectionStatus
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateIntegrationConnectionStatus sets isConnected to true for matching provider`() {
        val state = IntegrationState(
            integrations = listOf(fitbitItem.copy(isConnected = false)).toImmutableList(),
        )

        val result = reducer.reduce(
            state,
            IntegrationIntent.UpdateIntegrationConnectionStatus(
                provider = IntegrationProvider.Fitbit,
                isConnected = true,
            ),
        )

        assertThat(result.integrations.first { it.provider == IntegrationProvider.Fitbit }.isConnected).isTrue()
    }

    @Test
    fun `UpdateIntegrationConnectionStatus sets isConnected to false for matching provider`() {
        val state = IntegrationState(
            integrations = listOf(mfpItem.copy(isConnected = true)).toImmutableList(),
        )

        val result = reducer.reduce(
            state,
            IntegrationIntent.UpdateIntegrationConnectionStatus(
                provider = IntegrationProvider.MyFitnessPal,
                isConnected = false,
            ),
        )

        assertThat(result.integrations.first { it.provider == IntegrationProvider.MyFitnessPal }.isConnected).isFalse()
    }

    @Test
    fun `UpdateIntegrationConnectionStatus updates isValid for matching provider`() {
        val state = IntegrationState(
            integrations = listOf(fitbitItem.copy(isValid = true)).toImmutableList(),
        )

        val result = reducer.reduce(
            state,
            IntegrationIntent.UpdateIntegrationConnectionStatus(
                provider = IntegrationProvider.Fitbit,
                isConnected = true,
                isValid = false,
            ),
        )

        assertThat(result.integrations.first { it.provider == IntegrationProvider.Fitbit }.isValid).isFalse()
    }

    @Test
    fun `UpdateIntegrationConnectionStatus does not change other providers`() {
        val state = IntegrationState(
            integrations = listOf(fitbitItem, mfpItem).toImmutableList(),
        )

        val result = reducer.reduce(
            state,
            IntegrationIntent.UpdateIntegrationConnectionStatus(
                provider = IntegrationProvider.Fitbit,
                isConnected = true,
            ),
        )

        val mfpResult = result.integrations.first { it.provider == IntegrationProvider.MyFitnessPal }
        assertThat(mfpResult.isConnected).isEqualTo(mfpItem.isConnected)
    }

    @Test
    fun `UpdateIntegrationConnectionStatus isValid defaults to true`() {
        val state = IntegrationState(
            integrations = listOf(fitbitItem.copy(isValid = false)).toImmutableList(),
        )

        val result = reducer.reduce(
            state,
            IntegrationIntent.UpdateIntegrationConnectionStatus(
                provider = IntegrationProvider.Fitbit,
                isConnected = true,
            ),
        )

        assertThat(result.integrations.first { it.provider == IntegrationProvider.Fitbit }.isValid).isTrue()
    }

    // -------------------------------------------------------------------------
    // Side-effect intents — state is returned unchanged
    // -------------------------------------------------------------------------

    @Test
    fun `LoadIntegrations returns state unchanged`() {
        val state = IntegrationState(
            integrations = listOf(fitbitItem).toImmutableList(),
            selectedIntegrationForDisconnect = mfpItem,
        )

        val result = reducer.reduce(state, IntegrationIntent.LoadIntegrations)

        assertThat(result.integrations).containsExactly(fitbitItem)
        assertThat(result.selectedIntegrationForDisconnect).isEqualTo(mfpItem)
    }

    @Test
    fun `OpenIntegration returns state unchanged`() {
        val state = IntegrationState(integrations = listOf(fitbitItem).toImmutableList())

        val result = reducer.reduce(state, IntegrationIntent.OpenIntegration(fitbitItem))

        assertThat(result.integrations).containsExactly(fitbitItem)
    }

    @Test
    fun `AddIntegration returns state unchanged`() {
        val state = IntegrationState(integrations = listOf(fitbitItem).toImmutableList())

        val result = reducer.reduce(state, IntegrationIntent.AddIntegration(IntegrationProvider.Fitbit))

        assertThat(result.integrations).containsExactly(fitbitItem)
    }

    @Test
    fun `OnBack returns state unchanged`() {
        val state = IntegrationState(
            integrations = listOf(fitbitItem).toImmutableList(),
            selectedIntegrationForDisconnect = mfpItem,
        )

        val result = reducer.reduce(state, IntegrationIntent.OnBack)

        assertThat(result.integrations).containsExactly(fitbitItem)
        assertThat(result.selectedIntegrationForDisconnect).isEqualTo(mfpItem)
    }

    @Test
    fun `StartOAuthFlow returns state unchanged`() {
        val state = IntegrationState(integrations = listOf(mfpItem).toImmutableList())

        val result = reducer.reduce(
            state,
            IntegrationIntent.StartOAuthFlow(IntegrationProvider.MyFitnessPal, "account-1"),
        )

        assertThat(result.integrations).containsExactly(mfpItem)
    }

    @Test
    fun `OAuthFlowCompleted returns state unchanged`() {
        val state = IntegrationState(integrations = listOf(fitbitItem).toImmutableList())

        val result = reducer.reduce(
            state,
            IntegrationIntent.OAuthFlowCompleted(IntegrationProvider.Fitbit),
        )

        assertThat(result.integrations).containsExactly(fitbitItem)
    }

    @Test
    fun `OAuthFlowFailed returns state unchanged`() {
        val state = IntegrationState(integrations = listOf(fitbitItem).toImmutableList())

        val result = reducer.reduce(
            state,
            IntegrationIntent.OAuthFlowFailed(IntegrationProvider.Fitbit, "network error"),
        )

        assertThat(result.integrations).containsExactly(fitbitItem)
    }

    @Test
    fun `CheckHealthConnectAvailability returns state unchanged`() {
        val state = IntegrationState(integrations = listOf(healthConnectItem).toImmutableList())

        val result = reducer.reduce(state, IntegrationIntent.CheckHealthConnectAvailability)

        assertThat(result.integrations).containsExactly(healthConnectItem)
    }

    @Test
    fun `NavigateToHealthConnect returns state unchanged`() {
        val state = IntegrationState(integrations = listOf(healthConnectItem).toImmutableList())

        val result = reducer.reduce(state, IntegrationIntent.NavigateToHealthConnect)

        assertThat(result.integrations).containsExactly(healthConnectItem)
    }

    @Test
    fun `RemoveHealthConnectIntegration returns state unchanged`() {
        val state = IntegrationState(integrations = listOf(healthConnectItem).toImmutableList())

        val result = reducer.reduce(state, IntegrationIntent.RemoveHealthConnectIntegration)

        assertThat(result.integrations).containsExactly(healthConnectItem)
    }

    @Test
    fun `ToggleHealthConnectIntegration returns state unchanged`() {
        val state = IntegrationState(integrations = listOf(healthConnectItem).toImmutableList())

        val result = reducer.reduce(
            state,
            IntegrationIntent.ToggleHealthConnectIntegration(healthConnectItem),
        )

        assertThat(result.integrations).containsExactly(healthConnectItem)
    }

    @Test
    fun `RequestNewIntegration returns state unchanged`() {
        val state = IntegrationState(integrations = listOf(fitbitItem).toImmutableList())

        val result = reducer.reduce(state, IntegrationIntent.RequestNewIntegration)

        assertThat(result).isEqualTo(state)
        assertThat(result.integrations).containsExactly(fitbitItem)
    }
}
