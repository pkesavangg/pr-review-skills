package com.dmdbrands.gurus.weight.domain.model.api.entry

import com.dmdbrands.gurus.weight.domain.enums.ProductType

/**
 * Category of an entry sent through the unified `POST /v3/entries/` API.
 *
 * @property value The string value used in the API payload.
 */
enum class EntryCategory(
    val value: String,
) {
    /** Weight + body-composition entry. */
    WEIGHT("weight"),

    /** Blood-pressure entry. */
    BP("bp"),

    /** Baby weight/length entry (write wired in Android 3 / MOB-381). */
    BABY("baby");

    companion object {
        /** Resolves a [EntryCategory] from its API [value]; null for unknown. */
        fun fromValue(value: String?): EntryCategory? =
            entries.firstOrNull { it.value == value }

        /**
         * Maps a [ProductType] to its entry [EntryCategory]. Note BP maps to `bp` (not the
         * account-level `blood_pressure` apiValue) — the entries API uses the short category.
         */
        fun fromProductType(productType: ProductType): EntryCategory = when (productType) {
            ProductType.MY_WEIGHT -> WEIGHT
            ProductType.BLOOD_PRESSURE -> BP
            ProductType.BABY -> BABY
        }
    }
}
