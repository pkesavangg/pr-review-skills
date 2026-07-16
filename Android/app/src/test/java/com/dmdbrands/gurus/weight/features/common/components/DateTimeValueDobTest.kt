package com.dmdbrands.gurus.weight.features.common.components

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.TimeZone

/**
 * Round-trip tests for date-of-birth handling (MOB-1499).
 *
 * The server persists the date-only `dob` as local-midnight expressed in UTC, so a picked
 * `1999-12-27` in IST is echoed back as `1999-12-26T18:30:00.000Z`. Reading the UTC date of that
 * instant dropped a day for zones east of UTC; [DateTimeValue.getEpochMillisFromIsoString] now
 * resolves it in the system-default zone so the round-trip is stable.
 */
class DateTimeValueDobTest {

  private lateinit var originalTimeZone: TimeZone

  @BeforeEach
  fun setUp() {
    originalTimeZone = TimeZone.getDefault()
  }

  @AfterEach
  fun tearDown() {
    TimeZone.setDefault(originalTimeZone)
  }

  private fun roundTrip(stored: String): String {
    val millis = DateTimeValue.getEpochMillisFromIsoString(stored)
    return DateTimeValue.getDateFormatFromMilliseconds(millis)
  }

  @Test
  fun `IST - server local-midnight instant round-trips to the picked calendar date`() {
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))

    // 1999-12-27 midnight IST == 1999-12-26T18:30:00Z (what the server echoes back).
    assertThat(roundTrip("1999-12-26T18:30:00.000Z")).isEqualTo("1999-12-27")
  }

  @Test
  fun `IST - bare yyyy-MM-dd date round-trips unchanged`() {
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))

    assertThat(roundTrip("1999-12-27")).isEqualTo("1999-12-27")
  }

  @Test
  fun `IST - UTC-midnight instant resolves to the same calendar date`() {
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))

    assertThat(roundTrip("1999-12-27T00:00:00.000Z")).isEqualTo("1999-12-27")
  }

  @Test
  fun `UTC - local-midnight instant round-trips to the picked calendar date`() {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    assertThat(roundTrip("1999-12-27T00:00:00.000Z")).isEqualTo("1999-12-27")
  }

  @Test
  fun `Central - server local-midnight instant round-trips to the picked calendar date`() {
    TimeZone.setDefault(TimeZone.getTimeZone("America/Chicago"))

    // 1999-12-27 midnight CST (UTC-6) == 1999-12-27T06:00:00Z.
    assertThat(roundTrip("1999-12-27T06:00:00.000Z")).isEqualTo("1999-12-27")
  }
}
