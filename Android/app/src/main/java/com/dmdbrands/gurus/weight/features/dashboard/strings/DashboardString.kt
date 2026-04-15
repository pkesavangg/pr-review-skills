package com.dmdbrands.gurus.weight.features.dashboard.strings

object DashboardString {
  const val Title = "Dashboard Metrics"
  const val DashboardSource = "Dashboard"
  const val BackContentDescription = "Back"
  const val SaveSuccessMessage = "Dashboard metrics saved successfully"
  const val SaveErrorMessage = "Failed to save dashboard metrics"
  const val RemoveMetricDescription = "Remove metric"
  const val AddMetricDescription = "Add metric"

  object ExitDialog {
    const val Title = "Exit Dashboard"
    const val Message = "Are you sure you want to exit the dashboard?"
  }

  object Bp {
    const val EntryAverageSuffix = "entry average"
    const val NoEntries = "no entries"

    object ThreeReadingAverage {
      const val Title = "Three Reading Average"
      const val WhyTitle = "Why We Take an Average"
      const val WhyBody = "Blood pressure changes throughout the day. Averaging three readings gives a more accurate result."
      const val LastReadingsTitle = "Last 3 readings"
      const val Mmhg = "mmhg"
      const val Pulse = "pulse"
      const val CloseContentDescription = "Close"
    }
  }

  object ResetDialog {
    const val Title = "Are you sure?"
    const val Message = "Your dashboard display metrics will reset to Weight Gurus default settings."
    const val ConfirmText = "Reset"
    const val CancelText = "Cancel"
  }

  object Loader {
    const val Save = "Saving..."
  }
}
