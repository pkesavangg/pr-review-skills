package com.dmdbrands.gurus.weight.domain.enums

/**
 * Biological sex of a baby profile, as sent to / received from the `/v3/baby/` API.
 *
 * @property value The string value used for API communication and local storage.
 */
enum class BabySex(
    val value: String,
) {
    /** Male option. */
    MALE("male"),

    /** Female option. */
    FEMALE("female"),

    /** Private (unspecified) option. */
    PRIVATE("private");

    companion object {
        /**
         * Resolves a [BabySex] from its API [value], falling back to [PRIVATE]
         * for null or unrecognised values so the UI/API boundary never crashes.
         */
        fun fromValue(value: String?): BabySex =
            entries.firstOrNull { it.value == value } ?: PRIVATE
    }
}
