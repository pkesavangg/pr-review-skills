package com.greatergoods.meapp.features.common.helper.form

import android.util.Patterns

class AppValidatorConfig {
    object Email {
        const val MIN_LENGTH = 1
        const val MAX_LENGTH = 100
        val PATTERN = Patterns.EMAIL_ADDRESS.toRegex()
    }

    object Password {
        const val MIN_LENGTH = 6
        const val MAX_LENGTH = 50
    }

    object Name {
        const val MIN_LENGTH = 1
        const val MAX_LENGTH = 100
    }

    object ZipCode {
        const val MIN_LENGTH = 1
        const val MAX_LENGTH = 6
    }

    object DateOfBirth {
        const val MIN_LENGTH = 10
        const val MAX_LENGTH = 10
    }

    object BMR {
        const val MIN = 800
        const val MAX = 5000
    }

    object MetabolicAge {
        const val MIN = 12
        const val MAX = 100
    }

    object BodyComp {
        const val MIN = 0
        const val MAX = 100
    }

    object WeightKg {
        const val MIN = 0
        const val MAX = 450
    }

    object WeightLb {
        const val MIN = 0
        const val MAX = 999
    }

    object HeightMm {
        const val MIN = 500
        const val MAX = 3000
    }

    object SKU {
        val PATTERN = Regex("^[0-9]{4}$")
    }
}
