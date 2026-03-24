package com.dmdbrands.gurus.weight.features.manualEntry.viewmodel

import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.features.common.helper.form.MultiFormGroup
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [EntryReducer].
 *
 * Tests the pure reducer branches that don't require complex form creation.
 * Branches like UpdateWeightUnit, UpdateDashboardType, UpdateMetricFieldsExpandedStatus,
 * UpdateForm, and the else fallthrough.
 */
class EntryReducerTest {

    private lateinit var reducer: EntryReducer

    /** Relaxed mock for the form — we don't need actual form internals for most reducer tests. */
    private val mockForm: MultiFormGroup<EntryForm> = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        reducer = EntryReducer()
    }

    // -------------------------------------------------------------------------
    // UpdateWeightUnit
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateWeightUnit sets weightMode to KG`() {
        val state = EntryState(form = mockForm, weightMode = WeightUnit.LB)

        val result = reducer.reduce(state, EntryIntent.UpdateWeightUnit(WeightUnit.KG))

        assertThat(result?.weightMode).isEqualTo(WeightUnit.KG)
    }

    @Test
    fun `UpdateWeightUnit sets weightMode to LB`() {
        val state = EntryState(form = mockForm, weightMode = WeightUnit.KG)

        val result = reducer.reduce(state, EntryIntent.UpdateWeightUnit(WeightUnit.LB))

        assertThat(result?.weightMode).isEqualTo(WeightUnit.LB)
    }

    @Test
    fun `UpdateWeightUnit preserves other state fields`() {
        val state = EntryState(
            form = mockForm,
            weightMode = WeightUnit.LB,
            isLoading = true,
            isMetricFieldsExpandedInitially = true,
            dashboardType = DashboardType.DASHBOARD_12_METRICS,
        )

        val result = reducer.reduce(state, EntryIntent.UpdateWeightUnit(WeightUnit.KG))

        assertThat(result?.isLoading).isTrue()
        assertThat(result?.isMetricFieldsExpandedInitially).isTrue()
        assertThat(result?.dashboardType).isEqualTo(DashboardType.DASHBOARD_12_METRICS)
    }

    // -------------------------------------------------------------------------
    // UpdateDashboardType
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateDashboardType sets dashboardType to 12 metrics`() {
        val state = EntryState(form = mockForm)

        val result = reducer.reduce(state, EntryIntent.UpdateDashboardType(DashboardType.DASHBOARD_12_METRICS))

        assertThat(result?.dashboardType).isEqualTo(DashboardType.DASHBOARD_12_METRICS)
    }

    @Test
    fun `UpdateDashboardType sets dashboardType to 4 metrics`() {
        val state = EntryState(
            form = mockForm,
            dashboardType = DashboardType.DASHBOARD_12_METRICS,
        )

        val result = reducer.reduce(state, EntryIntent.UpdateDashboardType(DashboardType.DASHBOARD_4_METRICS))

        assertThat(result?.dashboardType).isEqualTo(DashboardType.DASHBOARD_4_METRICS)
    }

    @Test
    fun `UpdateDashboardType preserves other state fields`() {
        val state = EntryState(
            form = mockForm,
            weightMode = WeightUnit.KG,
            isLoading = true,
        )

        val result = reducer.reduce(state, EntryIntent.UpdateDashboardType(DashboardType.DASHBOARD_12_METRICS))

        assertThat(result?.weightMode).isEqualTo(WeightUnit.KG)
        assertThat(result?.isLoading).isTrue()
    }

    // -------------------------------------------------------------------------
    // UpdateMetricFieldsExpandedStatus
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateMetricFieldsExpandedStatus sets expanded to true`() {
        val state = EntryState(form = mockForm, isMetricFieldsExpandedInitially = false)

        val result = reducer.reduce(state, EntryIntent.UpdateMetricFieldsExpandedStatus(true))

        assertThat(result?.isMetricFieldsExpandedInitially).isTrue()
    }

    @Test
    fun `UpdateMetricFieldsExpandedStatus sets expanded to false`() {
        val state = EntryState(form = mockForm, isMetricFieldsExpandedInitially = true)

        val result = reducer.reduce(state, EntryIntent.UpdateMetricFieldsExpandedStatus(false))

        assertThat(result?.isMetricFieldsExpandedInitially).isFalse()
    }

    // -------------------------------------------------------------------------
    // UpdateForm
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateForm replaces form in state`() {
        val newForm: MultiFormGroup<EntryForm> = mockk(relaxed = true)
        val state = EntryState(form = mockForm)

        val result = reducer.reduce(state, EntryIntent.UpdateForm(newForm))

        assertThat(result?.form).isEqualTo(newForm)
    }

    @Test
    fun `UpdateForm preserves other state fields`() {
        val newForm: MultiFormGroup<EntryForm> = mockk(relaxed = true)
        val state = EntryState(
            form = mockForm,
            weightMode = WeightUnit.KG,
            dashboardType = DashboardType.DASHBOARD_12_METRICS,
            isMetricFieldsExpandedInitially = true,
        )

        val result = reducer.reduce(state, EntryIntent.UpdateForm(newForm))

        assertThat(result?.weightMode).isEqualTo(WeightUnit.KG)
        assertThat(result?.dashboardType).isEqualTo(DashboardType.DASHBOARD_12_METRICS)
        assertThat(result?.isMetricFieldsExpandedInitially).isTrue()
    }

    // -------------------------------------------------------------------------
    // else branch — side-effect-only intents return state unchanged
    // -------------------------------------------------------------------------

    @Test
    fun `Save returns state unchanged`() {
        val state = EntryState(form = mockForm, weightMode = WeightUnit.KG)

        val result = reducer.reduce(state, EntryIntent.Save)

        assertThat(result).isEqualTo(state)
    }

    @Test
    fun `EarlyExit returns state unchanged`() {
        val state = EntryState(form = mockForm)

        val result = reducer.reduce(state, EntryIntent.EarlyExit)

        assertThat(result).isEqualTo(state)
    }

    @Test
    fun `UpdateOnRelaunch returns state unchanged`() {
        val state = EntryState(form = mockForm)

        val result = reducer.reduce(state, EntryIntent.UpdateOnRelaunch)

        assertThat(result).isEqualTo(state)
    }

    // -------------------------------------------------------------------------
    // Default EntryState sanity
    // -------------------------------------------------------------------------

    @Test
    fun `default EntryState has expected initial values`() {
        val state = EntryState(form = mockForm)

        assertThat(state.weightMode).isEqualTo(WeightUnit.LB)
        assertThat(state.isLoading).isFalse()
        assertThat(state.isMetricFieldsExpandedInitially).isFalse()
        assertThat(state.dashboardType).isEqualTo(DashboardType.DASHBOARD_4_METRICS)
    }
}
