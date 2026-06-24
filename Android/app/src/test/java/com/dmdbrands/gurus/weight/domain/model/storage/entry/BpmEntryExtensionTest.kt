package com.dmdbrands.gurus.weight.domain.model.storage.entry

import com.dmdbrands.gurus.weight.testutil.TestFixtures
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class BpmEntryExtensionTest {

    @Test
    fun `toBpmSummary maps systolic, diastolic, pulse from BpmEntry`() {
        val entry = TestFixtures.aBpmEntry(systolic = 130, diastolic = 85, pulse = 60)

        val summary = entry.toBpmSummary()

        assertThat(summary.avgSystolic).isEqualTo(130)
        assertThat(summary.avgDiastolic).isEqualTo(85)
        assertThat(summary.avgPulse).isEqualTo(60)
    }

    @Test
    fun `toBpmSummary sets entryTimestamp from EntryEntity`() {
        val timestamp = "2024-06-15T08:30:00.000Z"
        val entry = TestFixtures.aBpmEntry(entryTimestamp = timestamp)

        val summary = entry.toBpmSummary()

        assertThat(summary.entryTimestamp).isEqualTo(timestamp)
    }

    @Test
    fun `toBpmSummary sets period to empty string`() {
        val entry = TestFixtures.aBpmEntry()

        val summary = entry.toBpmSummary()

        assertThat(summary.period).isEmpty()
    }

    @Test
    fun `toBpmSummary with zero systolic preserves zero value`() {
        val entry = TestFixtures.aBpmEntry(systolic = 0, diastolic = 0, pulse = 0)

        val summary = entry.toBpmSummary()

        assertThat(summary.avgSystolic).isEqualTo(0)
        assertThat(summary.avgDiastolic).isEqualTo(0)
        assertThat(summary.avgPulse).isEqualTo(0)
    }
}
