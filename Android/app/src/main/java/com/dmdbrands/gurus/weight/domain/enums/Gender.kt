package com.dmdbrands.gurus.weight.domain.enums

/**
 * Represents gender selection in signup
 *
 * @property value The string value used for API communication and storage
 */
enum class Gender(
    val value: String,
) {
    /** Male gender option */
    MALE("male"),

    /** Female gender option */
    FEMALE("female"),
}
