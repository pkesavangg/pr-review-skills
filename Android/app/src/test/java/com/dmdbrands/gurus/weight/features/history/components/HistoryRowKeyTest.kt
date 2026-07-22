package com.dmdbrands.gurus.weight.features.history.components

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Regression guard for the duplicate-month LazyColumn crash (MOB-1496 / PR #2291).
 *
 * WeightHistoryList / BpHistoryList use [historyRowKey] as the LazyColumn item key. Duplicate keys
 * throw "key already used" in a LazyColumn, so when two month rows collapse to the same
 * entryTimestamp (the duplicate-month case) the derived keys must still be unique.
 */
class HistoryRowKeyTest {

    @Test
    fun `two rows sharing a timestamp produce unique keys`() {
        // The duplicate-month case: identical entryTimestamp on two different rows.
        val first = historyRowKey("2023-10", index = 0)
        val second = historyRowKey("2023-10", index = 1)

        assertThat(first).isEqualTo("2023-10#0")
        assertThat(second).isEqualTo("2023-10#1")
        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun `keys across a full duplicate-heavy list are all distinct`() {
        // Every timestamp collides, yet folding the index in keeps all keys unique (no LazyColumn crash).
        val timestamps = listOf("2023-10", "2023-10", "2023-10", null, null)
        val keys = timestamps.mapIndexed { index, ts -> historyRowKey(ts, index) }

        assertThat(keys).containsNoDuplicates()
        assertThat(keys).hasSize(timestamps.size)
    }

    @Test
    fun `null timestamp falls back to month prefix`() {
        assertThat(historyRowKey(null, index = 3)).isEqualTo("month#3")
    }
}
