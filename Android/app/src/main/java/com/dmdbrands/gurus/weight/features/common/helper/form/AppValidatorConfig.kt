package com.dmdbrands.gurus.weight.features.common.helper.form

class AppValidatorConfig {
    object Patterns {
        val EMAIL = """^(([^<>()\[\]\\.,;:\s@"]+(\.[^<>()\[\]\\.,;:\s@"]+)*)|(".+"))@(([^<>()\[\]\\.,;:\s@"]+\.)+[^<>()\[\]\\.,;:\s@"]{2,})$"""
            .toRegex(RegexOption.IGNORE_CASE)
    }

    object Email {
        const val MIN_LENGTH = 1
        const val MAX_LENGTH = 100
        val PATTERN = Patterns.EMAIL
    }

    object Password {
        const val MIN_LENGTH = 6
        const val MAX_LENGTH = 50
    }

  object BMI {
    const val MAX_VALUE = 999
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

    object BMR {
        const val MIN = 0
        const val MAX = 10000
    }

    object MetabolicAge {
        const val MIN = 0
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

    object WeightLength {
        const val MAX_LENGTH = 4
    }

    object HeightMm {
        const val MIN = 500
        const val MAX = 3000
    }

    object SKU {
        val PATTERN = Regex("^[0-9]{4}$")
    }

    // Blood-pressure manual entry mirrors Balance Health (bpmMobileApp4):
    // values outside WARN_MIN..WARN_MAX show an advisory warning but still save;
    // only values above HARD_MAX are blocked.
    object Systolic {
        const val WARN_MIN = 60
        const val WARN_MAX = 250
        const val HARD_MAX = 500
    }

    object Diastolic {
        const val WARN_MIN = 30
        const val WARN_MAX = 150
        const val HARD_MAX = 500
    }

    object Pulse {
        const val WARN_MIN = 20
        const val WARN_MAX = 200
        const val HARD_MAX = 500
    }

    // Baby manual entry mirrors Smart Baby (babyApp): bounds are exclusive
    // (value must be strictly inside MIN..MAX) and oz/height accept one decimal.
    object BabyWeightLb {
        const val MIN = 0
        const val MAX = 1000 // allows whole lb up to 999 (Smart Baby imperial cap)
    }

    object BabyWeightOz {
        const val MIN = 0
        const val MAX = 16 // allows up to 15.9 oz (16 oz = 1 lb)
    }

    object BabyHeight {
        const val MIN = 0
        const val MAX = 100 // allows up to 99.9 in (Smart Baby imperial cap)
    }
}
