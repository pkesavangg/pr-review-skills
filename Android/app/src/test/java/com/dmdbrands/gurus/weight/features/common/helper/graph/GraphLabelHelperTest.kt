package com.dmdbrands.gurus.weight.features.common.helper.graph

import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodSummary
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

// TODO(MA-3965): These tests are written and compile, but the unit-test source set on
//  phase2-dev currently fails to COMPILE due to pre-existing migration debt unrelated to
//  this change — many test files reference classes removed in the architecture refactor
//  (e.g. GraphReducer, DashboardViewModel/Reducer, EntryAggregationService, EntryCrud/Sync
//  Service) and stale APIs (Toast -> Toast.Simple, changed ctor params). Until those tests
//  are migrated/deleted, `./gradlew test` and jacoco coverage cannot run, so this class
//  cannot execute. Re-enable coverage once the suite compiles.
class GraphLabelHelperTest {

  /** Minimal [PeriodSummary] fake with a controllable timestamp — avoids ISO parsing. */
  private fun summary(timestamp: Long): PeriodSummary = object : PeriodSummary {
    override val period: String = timestamp.toString()
    override val entryTimestamp: String = timestamp.toString()
    override fun getTimeStamp(): Long = timestamp
  }

  // ── selectionLabel ──────────────────────────────────────────────────────────

  @Test
  fun `no selection reads segment average`() {
    assertThat(GraphLabelHelper.selectionLabel(GraphSegment.WEEK, hasSelection = false, isLatestDaySelected = false))
      .isEqualTo("week average")
    assertThat(GraphLabelHelper.selectionLabel(GraphSegment.MONTH, hasSelection = false, isLatestDaySelected = false))
      .isEqualTo("month average")
    assertThat(GraphLabelHelper.selectionLabel(GraphSegment.YEAR, hasSelection = false, isLatestDaySelected = false))
      .isEqualTo("year average")
    assertThat(GraphLabelHelper.selectionLabel(GraphSegment.TOTAL, hasSelection = false, isLatestDaySelected = false))
      .isEqualTo("total average")
  }

  @Test
  fun `week or month with latest day selected reads latest entry`() {
    assertThat(GraphLabelHelper.selectionLabel(GraphSegment.WEEK, hasSelection = true, isLatestDaySelected = true))
      .isEqualTo("latest entry")
    assertThat(GraphLabelHelper.selectionLabel(GraphSegment.MONTH, hasSelection = true, isLatestDaySelected = true))
      .isEqualTo("latest entry")
  }

  @Test
  fun `week or month with non-latest day selected reads day average`() {
    assertThat(GraphLabelHelper.selectionLabel(GraphSegment.WEEK, hasSelection = true, isLatestDaySelected = false))
      .isEqualTo("day average")
    assertThat(GraphLabelHelper.selectionLabel(GraphSegment.MONTH, hasSelection = true, isLatestDaySelected = false))
      .isEqualTo("day average")
  }

  @Test
  fun `year and total always read month average on selection regardless of latest flag`() {
    assertThat(GraphLabelHelper.selectionLabel(GraphSegment.YEAR, hasSelection = true, isLatestDaySelected = true))
      .isEqualTo("month average")
    assertThat(GraphLabelHelper.selectionLabel(GraphSegment.TOTAL, hasSelection = true, isLatestDaySelected = true))
      .isEqualTo("month average")
    assertThat(GraphLabelHelper.selectionLabel(GraphSegment.TOTAL, hasSelection = true, isLatestDaySelected = false))
      .isEqualTo("month average")
  }

  // ── isLatestDaySelected ─────────────────────────────────────────────────────

  @Test
  fun `isLatestDaySelected is false when marker is null`() {
    assertThat(GraphLabelHelper.isLatestDaySelected(null, listOf(summary(100L), summary(200L)))).isFalse()
  }

  @Test
  fun `isLatestDaySelected is false when data is empty`() {
    assertThat(GraphLabelHelper.isLatestDaySelected(200.0, emptyList())).isFalse()
  }

  @Test
  fun `isLatestDaySelected is true when marker matches the latest timestamp`() {
    val data = listOf(summary(100L), summary(300L), summary(200L))
    assertThat(GraphLabelHelper.isLatestDaySelected(300.0, data)).isTrue()
  }

  @Test
  fun `isLatestDaySelected is false when marker matches an earlier day`() {
    val data = listOf(summary(100L), summary(300L), summary(200L))
    assertThat(GraphLabelHelper.isLatestDaySelected(200.0, data)).isFalse()
    assertThat(GraphLabelHelper.isLatestDaySelected(100.0, data)).isFalse()
  }

  @Test
  fun `isLatestDaySelected is false when marker matches no point`() {
    val data = listOf(summary(100L), summary(300L))
    assertThat(GraphLabelHelper.isLatestDaySelected(250.0, data)).isFalse()
  }
}
