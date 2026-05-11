package com.dmdbrands.gurus.weight.features.settings.strings

/**
 * Strings for the RadioGroupModal component.
 * Contains default text values and common labels.
 */
object RadioGroupModalStrings {

    // Common radio group titles
    object Titles {
        const val BiologicalSex = "Biological Sex"
        const val ActivityLevel = "Activity Level"
        const val UnitType = "Unit Type"
        const val Notifications = "Notifications"
        const val Weightless = "Weightless"
        const val Appearance = "Appearance"
    }

    object Button {
      const val Save = "Save"
      const val Ok = "OK"
      const val Cancel = "Cancel"
    }

    // Biological Sex Options
    object BiologicalSex {
        const val Male = "Male"
        const val Female = "Female"
    }

    // Activity Level Options
    object ActivityLevel {
        const val Normal = "Normal"
        const val Athlete = "Athlete"
    }

    // Unit Type Options
    object UnitType {
        const val Imperial = "lbs / in"
        const val Metric = "kg / cm"
        const val ImperialBaby = "lbs & oz / in"
    }

    // Notification Options (following Angular pattern)
    object Notifications {
      const val On = "Enable Notifications without Weight"
      const val WithWeight = "Enable Notifications with Weight"
      const val Off = "Disable notifications"
    }

    // Appearance Options
    object Appearance {
        const val Light = "Light"
        const val Dark = "Dark"
        const val System = "System Settings"
    }
}
