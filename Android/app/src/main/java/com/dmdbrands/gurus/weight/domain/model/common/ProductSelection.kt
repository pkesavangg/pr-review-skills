package com.dmdbrands.gurus.weight.domain.model.common

import com.dmdbrands.gurus.weight.domain.enums.ProductType

sealed class ProductSelection {

    abstract val productType: ProductType

    data object MyWeight : ProductSelection() {
        override val productType = ProductType.MY_WEIGHT
    }

    data object BloodPressure : ProductSelection() {
        override val productType = ProductType.BLOOD_PRESSURE
    }

    data class Baby(val profile: BabyProfile) : ProductSelection() {
        override val productType = ProductType.BABY
    }
}
