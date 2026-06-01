package com.dmdbrands.gurus.weight.domain.enums

enum class ProductType(val id: String, val displayName: String, val apiValue: String) {
    MY_WEIGHT(id = "weight_scale", displayName = "Weight Scale", apiValue = "weight"),
    BLOOD_PRESSURE(id = "blood_pressure", displayName = "Blood Pressure Monitor", apiValue = "blood_pressure"),
    BABY(id = "baby_scale", displayName = "Baby Scale", apiValue = "baby");

    companion object {
        val ALL: Set<ProductType> = entries.toSet()

        fun fromId(id: String): ProductType? = entries.firstOrNull { it.id == id }

        /** Resolves from the account API `productTypes` value (`weight`/`blood_pressure`/`baby`). */
        fun fromApiValue(value: String?): ProductType? = entries.firstOrNull { it.apiValue == value }
    }
}
