package com.dmdbrands.gurus.weight.features.manualEntry.helper

import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Unit tests for [EntryHelper.historyDetailKey].
 *
 * Regression for the saved-to-log VIEW action opening a blank History detail (MOB-598): the detail
 * query matches the History list's *bucketed* key — a "Mon YYYY" month label for weight/BP and a
 * "yyyy-MM-dd" day key for baby — not the raw ISO entryTimestamp. Keys are derived in UTC to mirror
 * the DAO's datetime(entryTimestamp,'utc','localtime') bucketing, so these assertions are stable
 * regardless of the machine's timezone.
 */
class EntryHelperHistoryDetailKeyTest {

  /** Stored exactly as the app writes entryTimestamp (UTC ISO). 2026-06-25T18:39:41Z. */
  private val juneIso = DateTimeConverter.timestampToIso(Instant.parse("2026-06-25T18:39:41.123Z").toEpochMilli())

  /** A December instant to exercise the month-abbreviation mapping. 2025-12-01T00:05:00Z. */
  private val decemberIso = DateTimeConverter.timestampToIso(Instant.parse("2025-12-01T00:05:00.000Z").toEpochMilli())

  @Test
  fun `weight key is the Mon YYYY month label`() {
    assertThat(EntryHelper.historyDetailKey(juneIso, ProductType.MY_WEIGHT)).isEqualTo("Jun 2026")
  }

  @Test
  fun `blood pressure key is the Mon YYYY month label`() {
    assertThat(EntryHelper.historyDetailKey(juneIso, ProductType.BLOOD_PRESSURE)).isEqualTo("Jun 2026")
  }

  @Test
  fun `baby key is the yyyy-MM-dd day key`() {
    assertThat(EntryHelper.historyDetailKey(juneIso, ProductType.BABY)).isEqualTo("2026-06-25")
  }

  @Test
  fun `month abbreviation matches the DAO CASE mapping for December`() {
    assertThat(EntryHelper.historyDetailKey(decemberIso, ProductType.MY_WEIGHT)).isEqualTo("Dec 2025")
    assertThat(EntryHelper.historyDetailKey(decemberIso, ProductType.BABY)).isEqualTo("2025-12-01")
  }

  @Test
  fun `key is a bucket label, never the raw ISO timestamp`() {
    val weightKey = EntryHelper.historyDetailKey(juneIso, ProductType.MY_WEIGHT)
    // The bug passed the raw ISO string straight to MonthDetails; guard against regressing to it.
    assertThat(weightKey).doesNotContain("T")
    assertThat(weightKey).isNotEqualTo(juneIso)
  }
}
