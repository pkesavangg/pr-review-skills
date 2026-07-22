package com.dmdbrands.gurus.weight.features.dashboard.string

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Guards the shared streak-count unit suffix (MOB-1168 / MOB-1498) used by BOTH the weight
 * milestone card (via StatHelper) and the Balance/BP dashboard streak cards:
 *   0 -> "days", 1 -> "day", 2-999 -> "days", 1000+ -> "d" (no maximum cap, prevents ellipsis
 *   truncation). Zero is plural so a zero/absent streak reads "0 days" (matches StreakZeroDays).
 */
class DashboardStringMileStoneTest {

    @Test
    fun `exactly one day uses singular day`() {
        assertThat(DashboardString.MileStone.streakDaySuffix(1)).isEqualTo("day")
    }

    @Test
    fun `zero days uses plural days`() {
        assertThat(DashboardString.MileStone.streakDaySuffix(0)).isEqualTo("days")
    }

    @Test
    fun `two through 999 days uses plural days`() {
        assertThat(DashboardString.MileStone.streakDaySuffix(2)).isEqualTo("days")
        assertThat(DashboardString.MileStone.streakDaySuffix(500)).isEqualTo("days")
        assertThat(DashboardString.MileStone.streakDaySuffix(999)).isEqualTo("days")
    }

    @Test
    fun `1000 and above uses abbreviated d with no cap`() {
        assertThat(DashboardString.MileStone.streakDaySuffix(1000)).isEqualTo("d")
        assertThat(DashboardString.MileStone.streakDaySuffix(5000)).isEqualTo("d")
    }
}
