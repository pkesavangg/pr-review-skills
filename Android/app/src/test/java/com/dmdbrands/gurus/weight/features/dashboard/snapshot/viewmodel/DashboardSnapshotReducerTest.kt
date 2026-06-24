package com.dmdbrands.gurus.weight.features.dashboard.snapshot.viewmodel

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [DashboardSnapshotReducer], focused on the [SnapshotChartData.isEmpty]
 * first-run flag introduced in MOB-432 and how it flows through each Set*Chart intent.
 */
class DashboardSnapshotReducerTest {

  private lateinit var reducer: DashboardSnapshotReducer

  @BeforeEach
  fun setUp() {
    reducer = DashboardSnapshotReducer()
  }

  @Test
  fun `default SnapshotChartData is not empty`() {
    assertThat(SnapshotChartData().isEmpty).isFalse()
  }

  @Test
  fun `SetWeightChart carries the isEmpty flag into weight state`() {
    val result = reducer.reduce(
      DashboardSnapshotState(),
      DashboardSnapshotIntent.SetWeightChart(SnapshotChartData(isEmpty = true)),
    )

    assertThat(result?.weight?.isEmpty).isTrue()
  }

  @Test
  fun `SetBpChart carries the isEmpty flag into bp state`() {
    val result = reducer.reduce(
      DashboardSnapshotState(),
      DashboardSnapshotIntent.SetBpChart(SnapshotChartData(isEmpty = true)),
    )

    assertThat(result?.bp?.isEmpty).isTrue()
  }

  @Test
  fun `SetBabyChart carries the isEmpty flag for the given profile`() {
    val result = reducer.reduce(
      DashboardSnapshotState(),
      DashboardSnapshotIntent.SetBabyChart("baby-1", SnapshotChartData(isEmpty = true)),
    )

    assertThat(result?.baby?.get("baby-1")?.isEmpty).isTrue()
  }

  @Test
  fun `populated chart data is not flagged empty`() {
    val result = reducer.reduce(
      DashboardSnapshotState(),
      DashboardSnapshotIntent.SetWeightChart(
        SnapshotChartData(label = "179.2", startTimestamp = 1L, endTimestamp = 2L),
      ),
    )

    assertThat(result?.weight?.isEmpty).isFalse()
    assertThat(result?.weight?.label).isEqualTo("179.2")
  }
}
