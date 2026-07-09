package com.dmdbrands.gurus.weight.features.dashboard.components

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Unit tests for [EmptyGraphDefaults] — the per-product Y ranges that drive the
 * dashboard "no entries" first-run grid (MOB-432).
 */
class EmptyGraphDefaultsTest {

  @Test
  fun `bp default range is 50 to 140 mmHg`() {
    assertThat(EmptyGraphDefaults.Bp).isEqualTo(EmptyGraphRange(50.0, 140.0, 30.0))
  }

  @Test
  fun `baby weight default range is 10 to 25 lbs`() {
    assertThat(EmptyGraphDefaults.BabyWeight).isEqualTo(EmptyGraphRange(10.0, 25.0, 5.0))
  }

  @Test
  fun `baby height default range is 15 to 35 in`() {
    assertThat(EmptyGraphDefaults.BabyHeight).isEqualTo(EmptyGraphRange(15.0, 35.0, 5.0))
  }

  @Test
  fun `weightGoal in lbs anchors a 5lb-step range around the goal`() {
    val range = EmptyGraphDefaults.weightGoal(goalDisplay = 178.0, isKg = false)

    // floor(178/5)*5 = 175 → 175,180,185,190 with the goal badge at 178.
    assertThat(range).isEqualTo(EmptyGraphRange(yMin = 175.0, yMax = 190.0, yStep = 5.0, goalValue = 178.0))
  }

  @Test
  fun `weightGoal in kg uses a 2kg step`() {
    val range = EmptyGraphDefaults.weightGoal(goalDisplay = 81.0, isKg = true)

    // floor(81/2)*2 = 80 → 80,82,84,86 with the goal badge at 81.
    assertThat(range).isEqualTo(EmptyGraphRange(yMin = 80.0, yMax = 86.0, yStep = 2.0, goalValue = 81.0))
  }

  @Test
  fun `weightGoal keeps the goal inside the rendered range`() {
    val range = requireNotNull(EmptyGraphDefaults.weightGoal(goalDisplay = 178.0, isKg = false))

    assertThat(range.goalValue).isAtLeast(range.yMin)
    assertThat(range.goalValue).isAtMost(range.yMax)
  }

  @Test
  fun `weightGoal returns null when no goal is set`() {
    assertThat(EmptyGraphDefaults.weightGoal(goalDisplay = null, isKg = false)).isNull()
  }

  @Test
  fun `weightGoal returns null for a non-positive goal`() {
    assertThat(EmptyGraphDefaults.weightGoal(goalDisplay = 0.0, isKg = false)).isNull()
  }

  // ── weightDefault — shown when no goal, so the weight empty grid still has a Y axis (like BP/Baby)

  @Test
  fun `weightDefault lbs range is 100 to 250 step 50`() {
    assertThat(EmptyGraphDefaults.weightDefault(isKg = false))
      .isEqualTo(EmptyGraphRange(100.0, 250.0, 50.0))
  }

  @Test
  fun `weightDefault kg range is 40 to 120 step 20`() {
    assertThat(EmptyGraphDefaults.weightDefault(isKg = true))
      .isEqualTo(EmptyGraphRange(40.0, 120.0, 20.0))
  }
}
