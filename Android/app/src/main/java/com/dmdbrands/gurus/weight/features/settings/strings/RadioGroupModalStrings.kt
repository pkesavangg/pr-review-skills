package com.dmdbrands.gurus.weight.features.settings.strings

import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment

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
        const val DefaultGraphRange = "Default Graph View"
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
        const val Imperial = "lbs / ft"
        const val Metric = "kg / cm"
        const val ImperialBaby = "lbs & oz / in"

        // Per-product section headers used by the sectioned Unit Type modal.
        const val MyWeightSection = "My Weight"
        const val MyKidsSection = "My Kids"
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

    // Default Graph Range Options
    object DefaultGraphRange {
        const val Week = "Week"
        const val Month = "Month"
        const val Year = "Year"
        const val Total = "Total"
    }
}

/**
 * Maps a [GraphSegment] enum to its user-facing display label defined in
 * [RadioGroupModalStrings.DefaultGraphRange]. Centralised here so callers across the
 * settings ViewModel and screen render labels consistently.
 */
fun GraphSegment.toDisplayString(): String = when (this) {
    GraphSegment.WEEK -> RadioGroupModalStrings.DefaultGraphRange.Week
    GraphSegment.MONTH -> RadioGroupModalStrings.DefaultGraphRange.Month
    GraphSegment.YEAR -> RadioGroupModalStrings.DefaultGraphRange.Year
    GraphSegment.TOTAL -> RadioGroupModalStrings.DefaultGraphRange.Total
}
