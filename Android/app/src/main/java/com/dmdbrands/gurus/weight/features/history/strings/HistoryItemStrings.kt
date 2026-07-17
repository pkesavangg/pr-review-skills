package com.dmdbrands.gurus.weight.features.history.strings

/**
 * Strings used in the HistoryItem composable.
 */
object HistoryItemStrings {
    const val Entries = "entries"
    const val Average = "average"
    const val Change = "change"
    const val GoToMonthView = "go to month view"
    const val BirthdayBalloonContentDescription = "birthday"
    const val AvgPressure = "avg pressure"
    const val AvgPulse = "avg pulse"
    const val Mmhg = "mmhg"
    const val Pulse = "pulse"
    const val Weight = "weight"
    const val Length = "length"
    const val Percent = "percent"

    /** Helper text shown in the expanded note area when an entry has no note yet (MOB-438, MOB-1163). */
    const val NoNoteYet = "no notes yet — tap plus icon to add one."

    /** Inline "read more" link appended to a note clamped to 2 lines (MOB-1499). */
    const val More = "more"

    /** TalkBack click action label for the clamped-note "more" expander (MOB-1499). */
    const val ShowFullNote = "show full note"
    const val EditNoteContentDescription = "edit entry"

    /** Content description for the add-note (+) affordance shown when an entry has no note (MOB-1163). */
    const val AddNoteContentDescription = "add note"
    const val ExpandNote = "expand note"
    const val CollapseNote = "collapse note"

    // region Accessibility (TalkBack)
    /** Connector word joining a count to its noun in a row announcement (e.g. "15 entries"). */
    const val accEntriesSuffix = "entries"

    /** Label spoken for the average-weight value in a weight history row (e.g. "average 70.5 lbs"). */
    const val accAverageLabel = "average"

    /** Label spoken for the change value in a weight history row (e.g. "change minus 1.2 lbs"). */
    const val accChangeLabel = "change"

    /** Label spoken for the average pressure in a BP history row (e.g. "average pressure 115 over 75"). */
    const val accAvgPressureLabel = "average pressure"

    /** Connector word read between systolic and diastolic in a BP announcement ("115 over 75"). */
    const val accOver = "over"

    /** Label spoken for the average pulse in a BP history row (e.g. "average pulse 60"). */
    const val accAvgPulseLabel = "average pulse"

    /** Label spoken for the weight value in a baby history row (e.g. "weight 8 lbs 14 oz"). */
    const val accWeightLabel = "weight"

    /** Label spoken for the length value in a baby history row (e.g. "length 12 in"). */
    const val accLengthLabel = "length"

    /** Label spoken for the percentile value in a baby history row (e.g. "percentile 6th"). */
    const val accPercentileLabel = "percentile"
    // endregion
}
