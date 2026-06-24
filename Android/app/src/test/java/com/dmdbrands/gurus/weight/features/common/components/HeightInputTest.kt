package com.dmdbrands.gurus.weight.features.common.components

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests for [HeightInput] conversions and the feet/cm picker range invariant.
 *
 * Regression coverage for MOB-172: switching units during account creation
 * (e.g. set 299 cm, toggle to ft/in) must produce a feet/inches value the
 * imperial picker can actually display. The ft/in picker tops out at 7'11";
 * taller metric heights (the cm picker allows up to 299 cm) are capped to
 * 7'11" on conversion. Previously the conversion produced an out-of-range
 * value (9'10") that the picker couldn't show, so the modal silently fell
 * back to the first item (2'10").
 */
class HeightInputTest {

  @Test
  fun `cm above the imperial maximum caps to 7 feet 11 inches`() {
    val ftIn = HeightInput.cmToFtIn(299)
    assertThat(ftIn).isEqualTo(HeightInput.FtIn(feet = 7, inches = 11))
  }

  @Test
  fun `241 cm and above maps to the 7 feet 11 inches cap`() {
    // 241 cm ≈ 94.9" → rounds to 95" = 7'11" (the ceiling).
    assertThat(HeightInput.cmToFtIn(241)).isEqualTo(HeightInput.FtIn(7, 11))
    assertThat(HeightInput.cmToFtIn(250)).isEqualTo(HeightInput.FtIn(7, 11))
  }

  @Test
  fun `a normal metric height converts without capping`() {
    // 178 cm ≈ 70" = 5'10".
    assertThat(HeightInput.cmToFtIn(178)).isEqualTo(HeightInput.FtIn(5, 10))
  }

  @Test
  fun `every selectable cm height converts to a value the picker can show`() {
    AppPickerDefaults.cmHeights.forEach { cm ->
      val ftIn = HeightInput.cmToFtIn(cm.value)
      assertThat(AppPickerDefaults.feetHeights).contains(ftIn.feet)
      assertThat(AppPickerDefaults.inchHeights).contains(ftIn.inches)
    }
  }

  @Test
  fun `fromStoredHeight imperial never exceeds 7 feet 11 inches`() {
    // 299 cm round-tripped through stored height must still cap at 7'11".
    val stored = HeightInput.Cm(299).toStoredHeight()
    val ftIn = HeightInput.fromStoredHeight(stored, isMetric = false) as HeightInput.FtIn
    assertThat(ftIn).isEqualTo(HeightInput.FtIn(7, 11))
  }

  @Test
  fun `cm round-trips through stored height`() {
    val original = HeightInput.Cm(200)
    val roundTripped = HeightInput.fromStoredHeight(original.toStoredHeight(), isMetric = true)
    assertThat(roundTripped).isEqualTo(original)
  }

  @Test
  fun `feet picker bounds match the declared min and max`() {
    assertThat(AppPickerDefaults.feetHeights.first()).isEqualTo(HeightInput.MIN_FEET)
    assertThat(AppPickerDefaults.feetHeights.last()).isEqualTo(HeightInput.MAX_FEET)
  }

  @Test
  fun `ft in below the metric minimum floors to 100 cm`() {
    // 2'0" ≈ 61 cm — below the cm picker's 100 cm minimum, so it floors to 100.
    assertThat(HeightInput.ftInToCm(2, 0)).isEqualTo(HeightInput.Cm(100))
    // 3'2" ≈ 96.5 cm → rounds to 97, still below 100 → floors to 100.
    assertThat(HeightInput.ftInToCm(3, 2)).isEqualTo(HeightInput.Cm(100))
  }

  @Test
  fun `a normal imperial height converts without flooring`() {
    // 5'10" = 70" ≈ 177.8 cm → rounds to 178.
    assertThat(HeightInput.ftInToCm(5, 10)).isEqualTo(HeightInput.Cm(178))
  }

  @Test
  fun `every selectable ft in height converts to a value the picker can show`() {
    AppPickerDefaults.feetHeights.forEach { feet ->
      AppPickerDefaults.inchHeights.forEach { inches ->
        val cm = HeightInput.ftInToCm(feet, inches)
        assertThat(AppPickerDefaults.cmHeights).contains(cm)
      }
    }
  }
}
