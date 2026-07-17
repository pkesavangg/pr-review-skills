package com.dmdbrands.gurus.weight.features.dashboard.snapshot.strings

object DashboardSnapshotStrings {
    const val WeekAverage = "week average"
    const val Lb = "lb"
    const val Lbs = "lbs"
    const val Oz = "oz"
    const val Inches = "in"
    const val Kg = "kg"
    const val Cm = "cm"
    const val St = "st"
    const val Mmhg = "mmhg"
    const val Pulse = "pulse"
    const val Weight = "weight"
    const val PlaceholderDash = "—"
    const val NoEntries = "no entries"

    // Zeroed first-run placeholders (shown when a product has no entries yet).
    const val ZeroWeight = "000.0"
    const val ZeroSystolic = "000"
    const val ZeroDiastolic = "00"
    const val ZeroPulse = "00"
    const val ZeroBabyLbs = "00"
    const val ZeroBabyOz = "0.0"
    const val OpenWeightDashboard = "Open weight dashboard"
    const val OpenBpDashboard = "Open blood pressure dashboard"
    const val OpenBabyDashboard = "Open baby dashboard"
    const val AppLogoDescription = "Me App"
    const val AhaRangeInfo = "AHA Range information"

    // region Accessibility (TalkBack)
    /**
     * Leading word for a snapshot line-chart summary. The metric value and time range
     * are composed onto this at the call site, e.g. "Chart showing 180 lb week average".
     */
    const val accChartSummaryPrefix = "Chart showing"

    /** Spoken descriptor for an empty snapshot chart (no entries yet). */
    const val accEmptyChartDescription = "Chart with no entries yet"
    // endregion
}
