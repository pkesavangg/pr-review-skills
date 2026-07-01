package com.dmdbrands.gurus.weight.domain.model.api.entry

/**
 * Source of an entry. For weight, the authoritative mapping remains
 * `DeviceSetupType.toSource(...)` (e.g. `btWifiR4`, `wifi scale`, `bluetooth scale`);
 * this enum captures the values the unified API documents for weight + BP.
 *
 * @property value The string value used in the API payload.
 */
enum class EntrySource(
    val value: String,
) {
    /** Manual form entry. */
    MANUAL("manual"),

    /** R4 WiFi + Bluetooth scale. */
    BT_WIFI_R4("btWifiR4"),

    /** Bluetooth device (BP monitor / scale). */
    BLUETOOTH("bluetooth"),

    /** WiFi device. */
    WIFI("wifi");

    companion object {
        /** Resolves a [EntrySource] from its API [value]; null for unknown. */
        fun fromValue(value: String?): EntrySource? =
            entries.firstOrNull { it.value == value }
    }
}
