package com.dmdbrands.gurus.weight.domain.model.api.entry

/**
 * Operation type for a unified entry payload. The wire value is lowercase
 * (`create`/`delete`), matching the legacy `ScaleApiEntry.operationType` encoding
 * (`EntryEntity.operationType` is stored uppercase via the local `OperationType`
 * enum and lowercased on the way out).
 *
 * @property value The string value used in the API payload.
 */
enum class EntryOperationType(
    val value: String,
) {
    CREATE("create"),
    DELETE("delete");

    companion object {
        /** Resolves from a stored/API value (case-insensitive); defaults to [CREATE]. */
        fun fromValue(value: String?): EntryOperationType =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: CREATE
    }
}
