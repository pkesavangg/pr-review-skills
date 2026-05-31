package com.dmdbrands.gurus.weight.domain.model.api.entry

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
    }
}
