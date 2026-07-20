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

    /**
     * Same-product identity (by type, and baby id for [Baby]) — independent of transient fields on
     * the wrapped [BabyProfile] such as `isSynced` / `existsOnServer`. Use this for "is this the
     * selected product" checks so a differently-sourced instance of the same baby still reads as
     * selected; structural `==` breaks when those sync flags differ between sources (MOB-1476).
     */
    fun isSameSelectionAs(other: ProductSelection?): Boolean = when {
        other == null -> false
        this is Baby && other is Baby -> profile.id == other.profile.id
        else -> productType == other.productType && this::class == other::class
    }
}
