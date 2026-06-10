package com.dmdbrands.gurus.weight.domain.enums

/**
 * Type of a baby entry sent through the unified `/v3/entries/` API (`category=baby`).
 *
 * Only `weight` and `measureLength` are in scope for Phase 2 (per the Baby App audit);
 * feeding/diaper/sleep/snapshot types are intentionally excluded.
 *
 * @property value The string value used for API communication and local storage.
 */
enum class BabyEntryType(
    val value: String,
) {
    /** Baby weight reading (`babyWeightDecigrams`). */
    WEIGHT("weight"),

    /** Baby length / height reading (`babyLengthMillimeters`). */
    MEASURE_LENGTH("measureLength");

    companion object {
        /**
         * Resolves a [BabyEntryType] from its API [value], falling back to [WEIGHT]
         * for null or unrecognised values.
         */
        fun fromValue(value: String?): BabyEntryType =
            entries.firstOrNull { it.value == value } ?: WEIGHT
    }
}
