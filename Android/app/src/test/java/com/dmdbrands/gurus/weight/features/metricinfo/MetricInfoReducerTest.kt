package com.dmdbrands.gurus.weight.features.metricinfo

import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.enums.MetricKey
import com.dmdbrands.gurus.weight.domain.model.storage.entry.DashboardMetric
import com.dmdbrands.gurus.weight.features.common.model.Stat
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [MetricInfoReducer].
 *
 * The reducer is a pure function — no mocking or coroutines needed.
 * Each test creates an initial state, dispatches an intent, and asserts the result.
 */
class MetricInfoReducerTest {

    private lateinit var reducer: MetricInfoReducer

    private val fakeStat: Stat = mockk(relaxed = true)
    private val fakeInfo: DashboardMetric = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        reducer = MetricInfoReducer()
    }

    // -------------------------------------------------------------------------
    // Default state
    // -------------------------------------------------------------------------

    @Test
    fun `default MetricInfoState has expected initial values`() {
        val state = MetricInfoState()

        assertThat(state.stat).isNull()
        assertThat(state.info).isNull()
        assertThat(state.selectedMetricIndex).isEqualTo(0)
        assertThat(state.isHeartRateOff).isFalse()
        assertThat(state.dashboardType).isNull()
    }

    // -------------------------------------------------------------------------
    // SetStat
    // -------------------------------------------------------------------------

    @Test
    fun `SetStat updates stat in state`() {
        val state = MetricInfoState(stat = null)

        val result = reducer.reduce(state, MetricInfoIntent.SetStat(fakeStat))

        assertThat(result?.stat).isEqualTo(fakeStat)
    }

    @Test
    fun `SetStat preserves all other fields`() {
        val state = MetricInfoState(
            info = fakeInfo,
            selectedMetricIndex = 2,
            isHeartRateOff = true,
            dashboardType = DashboardType.DASHBOARD_4_METRICS,
        )

        val result = reducer.reduce(state, MetricInfoIntent.SetStat(fakeStat))

        assertThat(result?.info).isEqualTo(fakeInfo)
        assertThat(result?.selectedMetricIndex).isEqualTo(2)
        assertThat(result?.isHeartRateOff).isTrue()
        assertThat(result?.dashboardType).isEqualTo(DashboardType.DASHBOARD_4_METRICS)
    }

    @Test
    fun `SetStat overwrites existing stat`() {
        val newStat: Stat = mockk(relaxed = true)
        val state = MetricInfoState(stat = fakeStat)

        val result = reducer.reduce(state, MetricInfoIntent.SetStat(newStat))

        assertThat(result?.stat).isEqualTo(newStat)
    }

    // -------------------------------------------------------------------------
    // SetMetricInfo
    // -------------------------------------------------------------------------

    @Test
    fun `SetMetricInfo updates info in state`() {
        val state = MetricInfoState(info = null)

        val result = reducer.reduce(state, MetricInfoIntent.SetMetricInfo(fakeInfo))

        assertThat(result?.info).isEqualTo(fakeInfo)
    }

    @Test
    fun `SetMetricInfo preserves all other fields`() {
        val state = MetricInfoState(
            stat = fakeStat,
            selectedMetricIndex = 3,
            isHeartRateOff = true,
            dashboardType = DashboardType.DASHBOARD_12_METRICS,
        )

        val result = reducer.reduce(state, MetricInfoIntent.SetMetricInfo(fakeInfo))

        assertThat(result?.stat).isEqualTo(fakeStat)
        assertThat(result?.selectedMetricIndex).isEqualTo(3)
        assertThat(result?.isHeartRateOff).isTrue()
        assertThat(result?.dashboardType).isEqualTo(DashboardType.DASHBOARD_12_METRICS)
    }

    @Test
    fun `SetMetricInfo overwrites existing info`() {
        val newInfo: DashboardMetric = mockk(relaxed = true)
        val state = MetricInfoState(info = fakeInfo)

        val result = reducer.reduce(state, MetricInfoIntent.SetMetricInfo(newInfo))

        assertThat(result?.info).isEqualTo(newInfo)
    }

    // -------------------------------------------------------------------------
    // SetSelectedIndex
    // -------------------------------------------------------------------------

    @Test
    fun `SetSelectedIndex updates selectedMetricIndex`() {
        val state = MetricInfoState(selectedMetricIndex = 0)

        val result = reducer.reduce(state, MetricInfoIntent.SetSelectedIndex(4))

        assertThat(result?.selectedMetricIndex).isEqualTo(4)
    }

    @Test
    fun `SetSelectedIndex to zero resets index`() {
        val state = MetricInfoState(selectedMetricIndex = 3)

        val result = reducer.reduce(state, MetricInfoIntent.SetSelectedIndex(0))

        assertThat(result?.selectedMetricIndex).isEqualTo(0)
    }

    @Test
    fun `SetSelectedIndex preserves other fields`() {
        val state = MetricInfoState(
            stat = fakeStat,
            info = fakeInfo,
            isHeartRateOff = true,
        )

        val result = reducer.reduce(state, MetricInfoIntent.SetSelectedIndex(2))

        assertThat(result?.stat).isEqualTo(fakeStat)
        assertThat(result?.info).isEqualTo(fakeInfo)
        assertThat(result?.isHeartRateOff).isTrue()
    }

    // -------------------------------------------------------------------------
    // SetHeartRateStatus
    // -------------------------------------------------------------------------

    @Test
    fun `SetHeartRateStatus true sets isHeartRateOff to true`() {
        val state = MetricInfoState(isHeartRateOff = false)

        val result = reducer.reduce(state, MetricInfoIntent.SetHeartRateStatus(true))

        assertThat(result?.isHeartRateOff).isTrue()
    }

    @Test
    fun `SetHeartRateStatus false sets isHeartRateOff to false`() {
        val state = MetricInfoState(isHeartRateOff = true)

        val result = reducer.reduce(state, MetricInfoIntent.SetHeartRateStatus(false))

        assertThat(result?.isHeartRateOff).isFalse()
    }

    @Test
    fun `SetHeartRateStatus preserves other fields`() {
        val state = MetricInfoState(
            selectedMetricIndex = 1,
            dashboardType = DashboardType.DASHBOARD_4_METRICS,
            stat = fakeStat,
        )

        val result = reducer.reduce(state, MetricInfoIntent.SetHeartRateStatus(true))

        assertThat(result?.selectedMetricIndex).isEqualTo(1)
        assertThat(result?.dashboardType).isEqualTo(DashboardType.DASHBOARD_4_METRICS)
        assertThat(result?.stat).isEqualTo(fakeStat)
    }

    // -------------------------------------------------------------------------
    // SetDashboardType
    // -------------------------------------------------------------------------

    @Test
    fun `SetDashboardType updates dashboardType`() {
        val state = MetricInfoState(dashboardType = null)

        val result = reducer.reduce(state, MetricInfoIntent.SetDashboardType(DashboardType.DASHBOARD_4_METRICS))

        assertThat(result?.dashboardType).isEqualTo(DashboardType.DASHBOARD_4_METRICS)
    }

    @Test
    fun `SetDashboardType blood pressure updates dashboardType`() {
        val state = MetricInfoState(dashboardType = DashboardType.DASHBOARD_4_METRICS)

        val result = reducer.reduce(state, MetricInfoIntent.SetDashboardType(DashboardType.DASHBOARD_12_METRICS))

        assertThat(result?.dashboardType).isEqualTo(DashboardType.DASHBOARD_12_METRICS)
    }

    @Test
    fun `SetDashboardType preserves other fields`() {
        val state = MetricInfoState(
            stat = fakeStat,
            info = fakeInfo,
            selectedMetricIndex = 2,
            isHeartRateOff = true,
        )

        val result = reducer.reduce(state, MetricInfoIntent.SetDashboardType(DashboardType.DASHBOARD_4_METRICS))

        assertThat(result?.stat).isEqualTo(fakeStat)
        assertThat(result?.info).isEqualTo(fakeInfo)
        assertThat(result?.selectedMetricIndex).isEqualTo(2)
        assertThat(result?.isHeartRateOff).isTrue()
    }

    // -------------------------------------------------------------------------
    // Side-effect intents — fall through to else -> state
    // -------------------------------------------------------------------------

    @Test
    fun `SelectSegment returns state unchanged`() {
        val state = MetricInfoState(
            stat = fakeStat,
            selectedMetricIndex = 1,
            dashboardType = DashboardType.DASHBOARD_4_METRICS,
        )

        val result = reducer.reduce(state, MetricInfoIntent.SelectSegment(MetricKey.WEIGHT))

        assertThat(result?.stat).isEqualTo(fakeStat)
        assertThat(result?.selectedMetricIndex).isEqualTo(1)
        assertThat(result?.dashboardType).isEqualTo(DashboardType.DASHBOARD_4_METRICS)
    }

    @Test
    fun `OpenResource returns state unchanged`() {
        val state = MetricInfoState(isHeartRateOff = true, selectedMetricIndex = 3)

        val result = reducer.reduce(state, MetricInfoIntent.OpenResource("https://example.com"))

        assertThat(result?.isHeartRateOff).isTrue()
        assertThat(result?.selectedMetricIndex).isEqualTo(3)
    }

    @Test
    fun `UpdateScaleMode returns state unchanged`() {
        val state = MetricInfoState(
            stat = fakeStat,
            info = fakeInfo,
            isHeartRateOff = false,
        )

        val result = reducer.reduce(state, MetricInfoIntent.UpdateScaleMode)

        assertThat(result?.stat).isEqualTo(fakeStat)
        assertThat(result?.info).isEqualTo(fakeInfo)
        assertThat(result?.isHeartRateOff).isFalse()
    }
}
