package com.dmdbrands.gurus.weight.features.common.helper

import com.dmdbrands.gurus.weight.domain.enums.MilestoneKey
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests the streak-milestone day/days/d display rule (MOB-1168):
 * 1 -> "day", 2-999 -> "days", 1000+ -> "d" (no maximum cap).
 */
class StatHelperTest {

  private fun streakSuffix(key: MilestoneKey, value: Int): String? =
    with(StatHelper) { DashboardKey.Milestone(key).toStat(value).valueSuffix }

  private fun streakDisplay(key: MilestoneKey, value: Int): String? =
    with(StatHelper) { DashboardKey.Milestone(key).toStat(value).getDisplayValue() }

  @Test
  fun `current streak of 1 uses singular day`() {
    assertThat(streakSuffix(MilestoneKey.CURRENT_STREAK, 1)).isEqualTo("day")
    assertThat(streakDisplay(MilestoneKey.CURRENT_STREAK, 1)).isEqualTo("1 day")
  }

  @Test
  fun `current streak of 2 uses plural days`() {
    assertThat(streakSuffix(MilestoneKey.CURRENT_STREAK, 2)).isEqualTo("days")
    assertThat(streakDisplay(MilestoneKey.CURRENT_STREAK, 2)).isEqualTo("2 days")
  }

  @Test
  fun `current streak of 999 uses plural days`() {
    assertThat(streakSuffix(MilestoneKey.CURRENT_STREAK, 999)).isEqualTo("days")
    assertThat(streakDisplay(MilestoneKey.CURRENT_STREAK, 999)).isEqualTo("999 days")
  }

  @Test
  fun `current streak of 1000 uses abbreviated d`() {
    assertThat(streakSuffix(MilestoneKey.CURRENT_STREAK, 1000)).isEqualTo("d")
    assertThat(streakDisplay(MilestoneKey.CURRENT_STREAK, 1000)).isEqualTo("1000 d")
  }

  @Test
  fun `current streak above 1000 stays abbreviated with no cap`() {
    assertThat(streakSuffix(MilestoneKey.CURRENT_STREAK, 5000)).isEqualTo("d")
    assertThat(streakDisplay(MilestoneKey.CURRENT_STREAK, 5000)).isEqualTo("5000 d")
  }

  @Test
  fun `longest streak follows the same day days d rule`() {
    assertThat(streakSuffix(MilestoneKey.LONGEST_STREAK, 1)).isEqualTo("day")
    assertThat(streakSuffix(MilestoneKey.LONGEST_STREAK, 500)).isEqualTo("days")
    assertThat(streakSuffix(MilestoneKey.LONGEST_STREAK, 1000)).isEqualTo("d")
  }

  @Test
  fun `zero streak has no display value so card shows its own empty state`() {
    // A streak of 0 is treated as no value; the "0 days" empty state is rendered by StatCard.
    assertThat(streakDisplay(MilestoneKey.CURRENT_STREAK, 0)).isNull()
  }
}
