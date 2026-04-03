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
        val state = EntryState(activeForm = ActiveEntryForm.Weight(form = mockForm), weightMode = WeightUnit.LB)

        val result = reducer.reduce(state, EntryIntent.UpdateWeightUnit(WeightUnit.KG))

        assertThat(result?.weightMode).isEqualTo(WeightUnit.KG)
    }

    @Test
    fun `UpdateWeightUnit sets weightMode to LB`() {
        val state = EntryState(activeForm = ActiveEntryForm.Weight(form = mockForm), weightMode = WeightUnit.KG)

        val result = reducer.reduce(state, EntryIntent.UpdateWeightUnit(WeightUnit.LB))

        assertThat(result?.weightMode).isEqualTo(WeightUnit.LB)
    }

    @Test
    fun `UpdateWeightUnit preserves other state fields`() {
        val state = EntryState(
            activeForm = ActiveEntryForm.Weight(form = mockForm),
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
        val state = EntryState(activeForm = ActiveEntryForm.Weight(form = mockForm))

        val result = reducer.reduce(state, EntryIntent.UpdateDashboardType(DashboardType.DASHBOARD_12_METRICS))

        assertThat(result?.dashboardType).isEqualTo(DashboardType.DASHBOARD_12_METRICS)
    }

    @Test
    fun `UpdateDashboardType sets dashboardType to 4 metrics`() {
        val state = EntryState(
            activeForm = ActiveEntryForm.Weight(form = mockForm),
            dashboardType = DashboardType.DASHBOARD_12_METRICS,
        )

        val result = reducer.reduce(state, EntryIntent.UpdateDashboardType(DashboardType.DASHBOARD_4_METRICS))

        assertThat(result?.dashboardType).isEqualTo(DashboardType.DASHBOARD_4_METRICS)
    }

    @Test
    fun `UpdateDashboardType preserves other state fields`() {
        val state = EntryState(
            activeForm = ActiveEntryForm.Weight(form = mockForm),
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
        val state = EntryState(activeForm = ActiveEntryForm.Weight(form = mockForm), isMetricFieldsExpandedInitially = false)

        val result = reducer.reduce(state, EntryIntent.UpdateMetricFieldsExpandedStatus(true))

        assertThat(result?.isMetricFieldsExpandedInitially).isTrue()
    }

    @Test
    fun `UpdateMetricFieldsExpandedStatus sets expanded to false`() {
        val state = EntryState(activeForm = ActiveEntryForm.Weight(form = mockForm), isMetricFieldsExpandedInitially = true)

        val result = reducer.reduce(state, EntryIntent.UpdateMetricFieldsExpandedStatus(false))

        assertThat(result?.isMetricFieldsExpandedInitially).isFalse()
    }

    // -------------------------------------------------------------------------
    // UpdateForm
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateForm replaces form in state`() {
        val newForm: MultiFormGroup<EntryForm> = mockk(relaxed = true)
        val state = EntryState(activeForm = ActiveEntryForm.Weight(form = mockForm))

        val result = reducer.reduce(state, EntryIntent.UpdateForm(newForm))

        assertThat(result?.form).isEqualTo(newForm)
    }

    @Test
    fun `UpdateForm preserves other state fields`() {
        val newForm: MultiFormGroup<EntryForm> = mockk(relaxed = true)
        val state = EntryState(
            activeForm = ActiveEntryForm.Weight(form = mockForm),
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
        val state = EntryState(activeForm = ActiveEntryForm.Weight(form = mockForm), weightMode = WeightUnit.KG)

        val result = reducer.reduce(state, EntryIntent.Save)

        assertThat(result).isEqualTo(state)
    }

    @Test
    fun `EarlyExit returns state unchanged`() {
        val state = EntryState(activeForm = ActiveEntryForm.Weight(form = mockForm))

        val result = reducer.reduce(state, EntryIntent.EarlyExit)

        assertThat(result).isEqualTo(state)
    }

    @Test
    fun `UpdateOnRelaunch returns state unchanged`() {
        val state = EntryState(activeForm = ActiveEntryForm.Weight(form = mockForm))

        val result = reducer.reduce(state, EntryIntent.UpdateOnRelaunch)

        assertThat(result).isEqualTo(state)
    }

    // -------------------------------------------------------------------------
    // Default EntryState sanity
    // -------------------------------------------------------------------------

    @Test
    fun `default EntryState has expected initial values`() {
        val state = EntryState(activeForm = ActiveEntryForm.Weight(form = mockForm))

        assertThat(state.weightMode).isEqualTo(WeightUnit.LB)
        assertThat(state.isLoading).isFalse()
        assertThat(state.isMetricFieldsExpandedInitially).isFalse()
        assertThat(state.dashboardType).isEqualTo(DashboardType.DASHBOARD_4_METRICS)
    }

    // -------------------------------------------------------------------------
    // LoadAppSyncData
    // -------------------------------------------------------------------------

    @Test
    fun `LoadAppSyncData creates new form with scale entry data`() {
        val mockScaleEntry: com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry =
            mockk(relaxed = true)
        val realForm = MultiFormGroup.create(forms = EntryForm.create())
        val state = EntryState(activeForm = ActiveEntryForm.Weight(form = realForm), weightMode = WeightUnit.LB)

        val result = reducer.reduce(
            state,
            EntryIntent.LoadAppSyncData(scaleEntry = mockScaleEntry, height = 170),
        )

        assertThat(result).isNotNull()
        assertThat(result?.form).isNotNull()
        // The form should be different from the original
        assertThat(result?.form).isNotEqualTo(realForm)
    }

    @Test
    fun `LoadAppSyncData preserves weightMode`() {
        val mockScaleEntry: com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry =
            mockk(relaxed = true)
        val realForm = MultiFormGroup.create(forms = EntryForm.create())
        val state = EntryState(activeForm = ActiveEntryForm.Weight(form = realForm), weightMode = WeightUnit.KG)

        val result = reducer.reduce(
            state,
            EntryIntent.LoadAppSyncData(scaleEntry = mockScaleEntry, height = 170),
        )

        assertThat(result?.weightMode).isEqualTo(WeightUnit.KG)
    }

    @Test
    fun `LoadAppSyncData with null height does not crash`() {
        val mockScaleEntry: com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry =
            mockk(relaxed = true)
        val realForm = MultiFormGroup.create(forms = EntryForm.create())
        val state = EntryState(activeForm = ActiveEntryForm.Weight(form = realForm))

        val result = reducer.reduce(
            state,
            EntryIntent.LoadAppSyncData(scaleEntry = mockScaleEntry, height = null),
        )

        assertThat(result).isNotNull()
    }

    // -------------------------------------------------------------------------
    // EntryForm.create — companion object
    // -------------------------------------------------------------------------

    @Test
    fun `EntryForm create returns form with expected structure`() {
        val form = EntryForm.create()

        assertThat(form.weightDateTime).isNotNull()
        assertThat(form.generalMetrics).isNotNull()
        assertThat(form.r4ScaleMetrics).isNull()
    }

    @Test
    fun `EntryForm create with includeR4ScaleMetrics includes r4 section`() {
        val form = EntryForm.create(includeR4ScaleMetrics = true)

        assertThat(form.r4ScaleMetrics).isNotNull()
    }

    @Test
    fun `EntryForm create with scaleEntry populates form fields`() {
        val mockScaleEntry: com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry =
            mockk(relaxed = true)

        val form = EntryForm.create(scaleEntry = mockScaleEntry, weightUnit = WeightUnit.LB)

        assertThat(form.weightDateTime).isNotNull()
        assertThat(form.generalMetrics).isNotNull()
    }

    // -------------------------------------------------------------------------
    // formatScaleEntryValue — tested indirectly via EntryForm.create(scaleEntry)
    // -------------------------------------------------------------------------

    @Test
    fun `EntryForm create with scaleEntry formats bmi via formatScaleEntryValue`() {
        val scaleEntryEntity = com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryEntity(
            id = 1L,
            weight = 800.0,
            bodyFat = null,
            muscleMass = null,
            water = null,
            bmi = 25.3,
            source = null,
        )
        val scaleEntryWithMetrics = com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntryWithMetrics(
            scaleEntry = scaleEntryEntity,
            scaleEntryMetric = null,
        )
        val entryEntity = com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity(
            id = 1L,
            accountId = "acc-1",
            entryTimestamp = "2026-01-01T00:00:00Z",
            operationType = "create",
            deviceType = "scale",
            deviceId = "dev-1",
            unit = WeightUnit.LB,
        )
        val scaleEntry = com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry(
            entry = entryEntity,
            scale = scaleEntryWithMetrics,
        )

        val form = EntryForm.create(scaleEntry = scaleEntry, weightUnit = WeightUnit.LB)

        // formatScaleEntryValue(25.3) => "25.3" -> 25.3f * 10 = 253.0f -> 253 -> "253"
        val bmiValue = form.generalMetrics.controls.bodyMassIndex.value
        assertThat(bmiValue).isEqualTo("253")
    }

    @Test
    fun `EntryForm create with scaleEntry formats bodyFat via formatScaleEntryValue`() {
        val scaleEntryEntity = com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryEntity(
            id = 1L,
            weight = 800.0,
            bodyFat = 18.5,
            muscleMass = 40.2,
            water = 55.0,
            bmi = null,
            source = null,
        )
        val scaleEntryWithMetrics = com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntryWithMetrics(
            scaleEntry = scaleEntryEntity,
            scaleEntryMetric = null,
        )
        val entryEntity = com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity(
            id = 1L,
            accountId = "acc-1",
            entryTimestamp = "2026-01-01T00:00:00Z",
            operationType = "create",
            deviceType = "scale",
            deviceId = "dev-1",
            unit = WeightUnit.KG,
        )
        val scaleEntry = com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry(
            entry = entryEntity,
            scale = scaleEntryWithMetrics,
        )

        val form = EntryForm.create(scaleEntry = scaleEntry, weightUnit = WeightUnit.KG)

        // formatScaleEntryValue(18.5) => "18.5" -> 18.5f * 10 = 185.0f -> 185 -> "185"
        assertThat(form.generalMetrics.controls.bodyFat.value).isEqualTo("185")
        // formatScaleEntryValue(40.2) => "40.2" -> 40.2f * 10 = 402.0f -> 402 -> "402"
        assertThat(form.generalMetrics.controls.muscleMass.value).isEqualTo("402")
        // formatScaleEntryValue(55.0) => "55.0" -> 55.0f * 10 = 550.0f -> 550 -> "550"
        assertThat(form.generalMetrics.controls.bodyWater.value).isEqualTo("550")
    }

    @Test
    fun `EntryForm create with null scaleEntry values returns empty strings`() {
        val scaleEntryEntity = com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryEntity(
            id = 1L,
            weight = 800.0,
            bodyFat = null,
            muscleMass = null,
            water = null,
            bmi = null,
            source = null,
        )
        val scaleEntryWithMetrics = com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntryWithMetrics(
            scaleEntry = scaleEntryEntity,
            scaleEntryMetric = null,
        )
        val entryEntity = com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity(
            id = 1L,
            accountId = "acc-1",
            entryTimestamp = "2026-01-01T00:00:00Z",
            operationType = "create",
            deviceType = "scale",
            deviceId = "dev-1",
            unit = WeightUnit.LB,
        )
        val scaleEntry = com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry(
            entry = entryEntity,
            scale = scaleEntryWithMetrics,
        )

        val form = EntryForm.create(scaleEntry = scaleEntry, weightUnit = WeightUnit.LB)

        // formatScaleEntryValue(null) => ""
        assertThat(form.generalMetrics.controls.bodyMassIndex.value).isEqualTo("")
        assertThat(form.generalMetrics.controls.bodyFat.value).isEqualTo("")
        assertThat(form.generalMetrics.controls.muscleMass.value).isEqualTo("")
        assertThat(form.generalMetrics.controls.bodyWater.value).isEqualTo("")
    }
}
