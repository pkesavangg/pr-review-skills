package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Pure-JVM unit tests for [computeListMaxHeight] — the single source of truth for the
 * "cap the account list to at most N tiles before scrolling" rule used by the multi-user
 * Landing screen (MOB-1572). The composable only wires this result into a height modifier,
 * so the membership and arithmetic here are what guarantee the CTAs stay on-screen.
 */
class AppSwipeableListHeightTest {

    private val tile = 76.dp

    @Test
    fun `no cap when maxVisibleItems is null`() {
        assertThat(
            computeListMaxHeight(
                maxVisibleItems = null,
                itemCount = 9,
                measuredItemHeight = tile,
            ),
        ).isNull()
    }

    @Test
    fun `no cap when item count is below the max`() {
        assertThat(
            computeListMaxHeight(
                maxVisibleItems = 5,
                itemCount = 3,
                measuredItemHeight = tile,
            ),
        ).isNull()
    }

    @Test
    fun `no cap when item count equals the max`() {
        assertThat(
            computeListMaxHeight(
                maxVisibleItems = 5,
                itemCount = 5,
                measuredItemHeight = tile,
            ),
        ).isNull()
    }

    @Test
    fun `caps to five measured tiles when six accounts overflow`() {
        assertThat(
            computeListMaxHeight(
                maxVisibleItems = 5,
                itemCount = 6,
                measuredItemHeight = tile,
            ),
        ).isEqualTo(tile * 5)
    }

    @Test
    fun `caps to five measured tiles when nine accounts overflow`() {
        // The regression case: 9 accounts must still be bounded to exactly 5 tile heights.
        assertThat(
            computeListMaxHeight(
                maxVisibleItems = 5,
                itemCount = 9,
                measuredItemHeight = tile,
            ),
        ).isEqualTo(tile * 5)
    }

    @Test
    fun `uses estimated tile height before an item has been measured`() {
        // measuredItemHeight == 0.dp means no item is measured yet (first layout pass);
        // the cap must still be applied so the list never expands unbounded.
        val estimated = 80.dp
        assertThat(
            computeListMaxHeight(
                maxVisibleItems = 5,
                itemCount = 9,
                measuredItemHeight = 0.dp,
                estimatedItemHeight = estimated,
            ),
        ).isEqualTo(estimated * 5)
    }

    @Test
    fun `prefers measured height over the estimate once measured`() {
        val estimated = 80.dp
        val measured = 90.dp
        assertThat(
            computeListMaxHeight(
                maxVisibleItems = 5,
                itemCount = 9,
                measuredItemHeight = measured,
                estimatedItemHeight = estimated,
            ),
        ).isEqualTo(measured * 5)
    }

    @Test
    fun `no cap when max visible items is not positive`() {
        assertThat(
            computeListMaxHeight(
                maxVisibleItems = 0,
                itemCount = 9,
                measuredItemHeight = tile,
            ),
        ).isNull()
    }
}
