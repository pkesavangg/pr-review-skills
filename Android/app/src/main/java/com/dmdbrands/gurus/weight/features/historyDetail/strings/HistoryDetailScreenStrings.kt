package com.dmdbrands.gurus.weight.features.historyDetail.strings

object HistoryDetailScreenStrings {
    const val BackButtonContentDescription = "Go back"
    const val EntryDetailContentDescription = "View entry details"
    const val MoreDetailsButton = "More Details"
    const val DeleteButton = "Delete"
    const val SaveButton = "Save"
    const val NoteSaveError = "Couldn't save note. Please try again."
    const val DeleteEntryContentDescription = "Delete entry"
    const val DeleteEntryDialogMessage = "Are you sure you want to delete your entry?"
    const val CancelButton = "Cancel"
    const val DeleteEntryDialogTitle = "Delete Entry?"
    const val DeleteLoaderMessage = "Deleting entry..."

    const val BpmUnit = "bpm"
    const val KcalUnit = "kcal"
    const val YearsUnit = "yrs"
    const val LevelUnit = "Lv."
    const val PercentageUnit = "%"

    // region Accessibility (TalkBack)
    /** Label spoken for the pressure value in a BP detail row (e.g. "pressure 120 over 80"). */
    const val accPressureLabel = "pressure"

    /** Connector word read between systolic and diastolic in a BP detail announcement. */
    const val accOver = "over"

    /** Label spoken for the pulse value in a BP detail row (e.g. "pulse 60"). */
    const val accPulseLabel = "pulse"

    /** Label spoken for the weight value in a weight detail row (e.g. "weight 150 lb"). */
    const val accWeightLabel = "weight"

    /** State suffix announced on an expandable detail row when it is open. */
    const val accExpandedState = "expanded"

    /** State suffix announced on an expandable detail row when it is closed. */
    const val accCollapsedState = "collapsed"
}
