package com.dmdbrands.gurus.weight.features.common.components

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Unit tests for [DatePickerDateConstraints] (MOB-1578).
 *
 * MOB-1578 enables Material 3's keyboard/text-input toggle on every date picker (including
 * date-of-birth). Typed dates are validated by Material 3 through the very same [year range] and
 * [isDateSelectable] predicate that gate calendar taps, so these tests lock in that the min/max
 * bounds keep applying regardless of how the date is entered.
 *
 * The timezone is pinned per-test because the local<->UTC conversions are timezone-dependent.
 */
class DatePickerDateConstraintsTest {

  private lateinit var originalTimeZone: TimeZone

  @BeforeEach
  fun setUp() {
    originalTimeZone = TimeZone.getDefault()
  }

  @AfterEach
  fun tearDown() {
    TimeZone.setDefault(originalTimeZone)
  }

  /** Local millis at midnight for a given calendar date, in the current default timezone. */
  private fun localDateMillis(year: Int, month0: Int, day: Int): Long =
    Calendar.getInstance().apply {
      set(year, month0, day, 0, 0, 0)
      set(Calendar.MILLISECOND, 0)
    }.timeInMillis

  // ---------------------------------------------------------------------------
  // calculateYearRange
  // ---------------------------------------------------------------------------

  @Test
  fun `year range spans the min and max years when both provided`() {
    TimeZone.setDefault(TimeZone.getTimeZone("America/Chicago"))
    val min = localDateMillis(1990, Calendar.JANUARY, 1)
    val max = localDateMillis(2020, Calendar.DECEMBER, 31)

    val range = DatePickerDateConstraints.calculateYearRange(min, max)

    assertThat(range.first).isEqualTo(1990)
    assertThat(range.last).isEqualTo(2020)
  }

  @Test
  fun `year range falls back to the default floor when min is null`() {
    val max = localDateMillis(2020, Calendar.JUNE, 1)

    val range = DatePickerDateConstraints.calculateYearRange(null, max)

    assertThat(range.first).isEqualTo(DatePickerDateConstraints.DEFAULT_MIN_YEAR)
    assertThat(range.last).isEqualTo(2020)
  }

  @Test
  fun `year range uses now for the ceiling when max is null`() {
    val min = localDateMillis(1980, Calendar.MARCH, 15)
    val now = localDateMillis(2031, Calendar.JULY, 20)

    val range = DatePickerDateConstraints.calculateYearRange(min, null, nowMillis = now)

    assertThat(range.first).isEqualTo(1980)
    assertThat(range.last).isEqualTo(2031)
  }

  // ---------------------------------------------------------------------------
  // local <-> UTC round trip
  // ---------------------------------------------------------------------------

  @Test
  fun `local to UTC date millis round-trips to the same calendar date east of UTC`() {
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
    val local = localDateMillis(1999, Calendar.DECEMBER, 27)

    val utc = DatePickerDateConstraints.localMillisToUtcDateMillis(local)
    val backToLocal = DatePickerDateConstraints.utcDateMillisToLocalMillis(utc)

    assertThat(backToLocal).isEqualTo(local)
  }

  @Test
  fun `local to UTC date millis round-trips to the same calendar date west of UTC`() {
    TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
    val local = localDateMillis(2000, Calendar.FEBRUARY, 29)

    val utc = DatePickerDateConstraints.localMillisToUtcDateMillis(local)
    val backToLocal = DatePickerDateConstraints.utcDateMillisToLocalMillis(utc)

    assertThat(backToLocal).isEqualTo(local)
  }

  // ---------------------------------------------------------------------------
  // isDateSelectable — the predicate that also gates typed input
  // ---------------------------------------------------------------------------

  @Test
  fun `typed date before the minimum is rejected`() {
    TimeZone.setDefault(TimeZone.getTimeZone("America/Chicago"))
    val min = utc(1990, Calendar.JANUARY, 1)
    val max = utc(2020, Calendar.JANUARY, 1)
    val today = utc(2031, Calendar.JULY, 20)
    val typed = utc(1989, Calendar.DECEMBER, 31)

    val selectable = DatePickerDateConstraints.isDateSelectable(typed, min, max, today)

    assertThat(selectable).isFalse()
  }

  @Test
  fun `typed date after the maximum is rejected`() {
    val min = utc(1990, Calendar.JANUARY, 1)
    val max = utc(2020, Calendar.JANUARY, 1)
    val today = utc(2031, Calendar.JULY, 20)
    val typed = utc(2020, Calendar.JANUARY, 2)

    val selectable = DatePickerDateConstraints.isDateSelectable(typed, min, max, today)

    assertThat(selectable).isFalse()
  }

  @Test
  fun `typed date within bounds is accepted`() {
    val min = utc(1990, Calendar.JANUARY, 1)
    val max = utc(2020, Calendar.JANUARY, 1)
    val today = utc(2031, Calendar.JULY, 20)
    val typed = utc(2005, Calendar.JUNE, 15)

    val selectable = DatePickerDateConstraints.isDateSelectable(typed, min, max, today)

    assertThat(selectable).isTrue()
  }

  @Test
  fun `null maximum falls back to today so future dates are rejected`() {
    val today = utc(2031, Calendar.JULY, 20)
    val future = utc(2031, Calendar.JULY, 21)

    val selectable = DatePickerDateConstraints.isDateSelectable(future, null, null, today)

    assertThat(selectable).isFalse()
  }

  @Test
  fun `null minimum leaves the lower bound open`() {
    val today = utc(2031, Calendar.JULY, 20)
    val old = utc(1900, Calendar.JANUARY, 1)

    val selectable = DatePickerDateConstraints.isDateSelectable(old, null, null, today)

    assertThat(selectable).isTrue()
  }

  // ---------------------------------------------------------------------------
  // isWithinConfirmBounds — the final OK-time guard
  // ---------------------------------------------------------------------------

  @Test
  fun `confirm bounds treat a null maximum as unbounded`() {
    val min = utc(1990, Calendar.JANUARY, 1)
    val future = utc(2999, Calendar.JANUARY, 1)

    assertThat(DatePickerDateConstraints.isWithinConfirmBounds(future, min, null)).isTrue()
    assertThat(DatePickerDateConstraints.isWithinConfirmBounds(utc(1989, Calendar.JANUARY, 1), min, null))
      .isFalse()
  }

  /** UTC midnight millis for a calendar date — mirrors the values the picker feeds the predicate. */
  private fun utc(year: Int, month0: Int, day: Int): Long =
    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
      set(year, month0, day, 0, 0, 0)
      set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
