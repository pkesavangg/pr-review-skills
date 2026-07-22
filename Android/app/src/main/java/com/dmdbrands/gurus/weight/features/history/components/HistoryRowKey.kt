package com.dmdbrands.gurus.weight.features.history.components

/**
 * Derives the stable-yet-unique LazyColumn item key for a history row.
 *
 * The month label alone can collide — duplicate / edge-case [entryTimestamp]s in the data can
 * render two rows with the same "MMM yyyy", and duplicate keys crash LazyColumn with a
 * "key already used" error. Folding the row [index] into the key guarantees uniqueness
 * regardless of the data (the duplicate-month case). A null timestamp falls back to "month".
 *
 * Extracted from [WeightHistoryList] / [BpHistoryList] so the collision guard is unit-testable
 * (MOB-1496, PR #2291 review).
 */
internal fun historyRowKey(entryTimestamp: String?, index: Int): String =
    "${entryTimestamp ?: "month"}#$index"
