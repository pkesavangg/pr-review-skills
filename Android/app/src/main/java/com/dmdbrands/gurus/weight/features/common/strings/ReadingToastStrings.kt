package com.dmdbrands.gurus.weight.features.common.strings

import com.dmdbrands.gurus.weight.domain.enums.ProductType

object ReadingToastStrings {
    fun title(type: ProductType): String = when (type) {
        ProductType.MY_WEIGHT -> "New Weight Scale Reading Received"
        ProductType.BLOOD_PRESSURE -> "New BPM Reading Received"
        ProductType.BABY -> "New Baby Scale Reading Received"
    }

    fun primaryAction(type: ProductType): String = when (type) {
        ProductType.BABY -> "ASSIGN"
        else -> "SAVE"
    }

    fun secondaryAction(type: ProductType): String = when (type) {
        ProductType.BABY -> "DON'T ASSIGN"
        else -> "DISCARD"
    }

    fun assignedTo(name: String): String = "Reading assigned to ${name.uppercase()}"

    const val WrongBaby = "Have you assigned to Wrong baby?"
    const val Reassign = "Reassign"
}
