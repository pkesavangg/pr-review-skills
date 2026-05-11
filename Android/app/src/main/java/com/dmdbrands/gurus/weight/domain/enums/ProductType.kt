package com.dmdbrands.gurus.weight.domain.enums

enum class ProductType(val id: String, val displayName: String) {
    MY_WEIGHT(id = "weight_scale", displayName = "Weight Scale"),
    BLOOD_PRESSURE(id = "blood_pressure", displayName = "Blood Pressure Monitor"),
    BABY(id = "baby_scale", displayName = "Baby Scale");

    companion object {
        val ALL: Set<ProductType> = entries.toSet()

        fun fromId(id: String): ProductType? = entries.firstOrNull { it.id == id }
    }
}
