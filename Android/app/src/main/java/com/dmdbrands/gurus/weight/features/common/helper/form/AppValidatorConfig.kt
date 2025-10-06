package com.dmdbrands.gurus.weight.features.common.helper.form

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
        const val MAX_LENGTH = 20
    }

    object DateOfBirth {
        const val DEFAULT_VALUE = "2000-01-01"
        const val MIN_LENGTH = 10
        const val MAX_LENGTH = 10
    }

    object VisceralAge {
        const val MIN = 1
        const val MAX = 60
    }

    object BMR {
        const val MIN = 800
        const val MAX = 10000
    }

    object MetabolicAge {
        const val MIN = 12
        const val MAX = 150
    }

    object BodyComp {
        const val MIN = 0
        const val MAX = 99
    }

  object HeartRate {
    const val MIN = 0
    const val MAX = 200
  }

  object VisceralFat {
    const val MIN = 0
    const val MAX = 60
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
