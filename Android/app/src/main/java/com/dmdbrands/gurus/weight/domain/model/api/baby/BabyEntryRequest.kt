package com.dmdbrands.gurus.weight.domain.model.api.baby

import com.dmdbrands.gurus.weight.domain.enums.BabyEntryType
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BabyEntry

/**
 * Request body for a baby entry sent through the unified `/v3/entries/` API
 * (`category=baby`).
 *
 * GATED: this DTO and its [toBabyEntryRequest] mapper are built and unit-tested
 * now, but are NOT sent live until the Unified Entries API lands
 * (MOB-379 write / MOB-380 read). The current `operation/r4` endpoint only
 * accepts [com.dmdbrands.gurus.weight.domain.model.api.entry.ScaleApiEntry].
 *
 * Only `weight` (`babyWeightDecigrams`) and `measureLength` (`babyLengthMillimeters`)
 * are in scope for Phase 2; the relevant value is populated per [entryType].
 */
data class BabyEntryRequest(
    val babyId: String,
    val entryType: String,
    val entryTimestamp: String,
    val operationType: String,
    val entryNote: String? = null,
    val babyWeightDecigrams: Int? = null,
    val babyLengthMillimeters: Int? = null,
    val source: String? = null,
)

/**
 * Maps a domain [BabyEntry] into a [BabyEntryRequest]. The weight/length value
 * is selected based on the entry's [BabyEntryType]; the non-applicable measure
 * is left null.
 */
fun BabyEntry.toBabyEntryRequest(): BabyEntryRequest {
    val type = BabyEntryType.fromValue(entryType)
    return BabyEntryRequest(
        babyId = babyId,
        entryType = type.value,
        entryTimestamp = entry.entryTimestamp,
        operationType = entry.operationType.lowercase(),
        entryNote = entryNote,
        babyWeightDecigrams = if (type == BabyEntryType.WEIGHT) babyWeightDecigrams else null,
        babyLengthMillimeters = if (type == BabyEntryType.MEASURE_LENGTH) babyLengthMillimeters else null,
        source = babyEntry.source,
    )
}
