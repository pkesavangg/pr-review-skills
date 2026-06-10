package com.dmdbrands.gurus.weight.features.dashboard.strings

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Guards the Phase 2.0 mockup strings corrected in MOB-443:
 *   - Select Graph bottom-sheet title
 *   - BP rolling-average label with the count spelled out
 */
class DashboardStringTest {

    @Test
    fun `select graph sheet title matches mockup`() {
        assertThat(DashboardString.SelectGraphTitle).isEqualTo("Select Graph")
    }

    @Test
    fun `entry average label spells out counts one through three`() {
        assertThat(DashboardString.Bp.entryAverageLabel(1)).isEqualTo("one entry average")
        assertThat(DashboardString.Bp.entryAverageLabel(2)).isEqualTo("two entry average")
        assertThat(DashboardString.Bp.entryAverageLabel(3)).isEqualTo("three entry average")
    }

    @Test
    fun `entry average label shows no entries when count is zero`() {
        assertThat(DashboardString.Bp.entryAverageLabel(0)).isEqualTo("no entries")
    }

    @Test
    fun `entry average label falls back to numeral above three`() {
        assertThat(DashboardString.Bp.entryAverageLabel(4)).isEqualTo("4 entry average")
    }
}
