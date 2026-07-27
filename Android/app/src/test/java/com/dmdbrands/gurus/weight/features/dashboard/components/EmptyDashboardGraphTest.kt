package com.dmdbrands.gurus.weight.features.dashboard.components

import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Covers [emptyGraphXLabels] — the per-segment empty-state X-axis labels that mirror the live
 * graph's current-period bottom axis (MOB-847). Regression guard for the bug where every segment
 * rendered the same sun…sat week-day labels in the empty state.
 *
 * Assertions are structural (label shape/count) rather than exact strings so they stay stable
 * across the machine time zone the JVM test runs in. [now] is fixed (~2026-06-15) for determinism.
 */
class EmptyDashboardGraphTest {

  private val now = 1_781_481_600_000L // ~2026-06-15

  @Test
  fun `week shows seven day-of-week labels starting sunday`() {
    val labels = emptyGraphXLabels(GraphSegment.WEEK, now)

    assertThat(labels).hasSize(7)
    assertThat(labels.first()).isEqualTo("sun")
    assertThat(labels).containsAtLeast("mon", "sat")
    // Day-of-week names, never numeric.
    assertThat(labels.all { it.toIntOrNull() == null }).isTrue()
  }

  @Test
  fun `month shows day-of-month numbers at weekly ticks`() {
    val labels = emptyGraphXLabels(GraphSegment.MONTH, now)

    assertThat(labels).isNotEmpty()
    // Every label is a valid day-of-month number (1..31) — not a week-day name.
    assertThat(labels.all { (it.toIntOrNull() ?: -1) in 1..31 }).isTrue()
  }

  @Test
  fun `year shows twelve month initials january to december`() {
    val labels = emptyGraphXLabels(GraphSegment.YEAR, now)

    assertThat(labels)
      .containsExactly("j", "f", "m", "a", "m", "j", "j", "a", "s", "o", "n", "d")
      .inOrder()
  }

  @Test
  fun `total shows no x labels`() {
    // Matches the live and release-5.0.4 TOTAL axis, which shows only year separators.
    assertThat(emptyGraphXLabels(GraphSegment.TOTAL, now)).isEmpty()
  }

  @Test
  fun `segments do not all share the same labels`() {
    val week = emptyGraphXLabels(GraphSegment.WEEK, now)
    val month = emptyGraphXLabels(GraphSegment.MONTH, now)
    val year = emptyGraphXLabels(GraphSegment.YEAR, now)

    assertThat(week).isNotEqualTo(month)
    assertThat(week).isNotEqualTo(year)
    assertThat(month).isNotEqualTo(year)
  }
}
