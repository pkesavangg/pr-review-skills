package com.dmdbrands.gurus.weight.features.signup.strings

object PickDeviceStrings {
    const val title = "What will you use with meApp?"

    // Supporting note: reassures users they aren't locked into one device (MOB-420).
    // TODO(MOB-420): confirm final copy with design — not yet specified in Figma/iOS.
    const val addLaterNote = "You can always add more devices later."

    // Device titles
    const val babyScaleTitle = "Baby Scale"
    const val bloodPressureTitle = "Blood Pressure Monitor"
    const val weightScaleTitle = "Weight Scale"

    // Device subtitles
    const val babyScaleSubtitle = "track baby growth"
    const val bloodPressureSubtitle = "bp trends & reminders"
    const val weightScaleSubtitle = "bmi & weight insights"

    // Subtitle shown on cards for devices already registered in this session
    const val alreadyAdded = "already added"

    // Device IDs
    object Devices {
        const val BABY_SCALE = "baby_scale"
        const val BLOOD_PRESSURE = "blood_pressure"
        const val WEIGHT_SCALE = "weight_scale"
    }
}
