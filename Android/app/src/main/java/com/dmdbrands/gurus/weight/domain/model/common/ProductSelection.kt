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

    /**
     * The user owns a baby scale but currently has no baby profiles (e.g. deleted
     * their last one, or completed Baby Scale signup but skipped baby details).
     * Keeps "Baby Scale" available in the product dropdown; baby surfaces render an
     * empty state with an `ADD A BABY` CTA. (MOB-416)
     */
    data object BabyScale : ProductSelection() {
        override val productType = ProductType.BABY
    }
}
