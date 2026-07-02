package com.dmdbrands.gurus.weight.features.common.components

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Pure-function coverage for [AppInputDefaults.filterValue], the field-level input filter.
 *
 * Regression guard for MOB-674: the four non-percent Body Metrics fields (heart rate,
 * visceral fat, basal metabolic rate, metabolic age) render with [AppInputType.NUMBER] and
 * must hard-reject non-numeric characters at the field level, exactly like the [AppInputType.BODY_COMP]
 * percent fields. Previously NUMBER fell through to the unfiltered branch and let letters/symbols
 * through, surfacing only as a post-entry "invalid number" validation error.
 */
class AppInputDefaultsTest {

  @Test
  fun `filterValue strips non-digits for NUMBER`() {
    assertThat(AppInputDefaults.filterValue(AppInputType.NUMBER, "12a3")).isEqualTo("123")
  }

  @Test
  fun `filterValue strips letters and symbols for NUMBER`() {
    assertThat(AppInputDefaults.filterValue(AppInputType.NUMBER, "abc")).isEqualTo("")
    assertThat(AppInputDefaults.filterValue(AppInputType.NUMBER, "9!9.9")).isEqualTo("999")
  }

  @Test
  fun `filterValue keeps pure digits unchanged for NUMBER`() {
    assertThat(AppInputDefaults.filterValue(AppInputType.NUMBER, "150")).isEqualTo("150")
  }

  @Test
  fun `filterValue strips non-digits for BODY_COMP`() {
    assertThat(AppInputDefaults.filterValue(AppInputType.BODY_COMP, "1a2.3%")).isEqualTo("123")
  }

  @Test
  fun `filterValue allows a single leading decimal point for DECIMAL_STRING`() {
    assertThat(AppInputDefaults.filterValue(AppInputType.DECIMAL_STRING, "1a2.3.4")).isEqualTo("12.34")
  }

  @Test
  fun `filterValue leaves TEXT untouched`() {
    assertThat(AppInputDefaults.filterValue(AppInputType.TEXT, "abc123")).isEqualTo("abc123")
  }
}
