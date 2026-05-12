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

    object AhaRatings {
      const val Title = "AHA Ratings"
      const val SectionTitle = "Blood Pressure Level Colors"
      const val SectionBody = "Colors show where your reading falls—from Normal to Hypertensive—based on American Heart Association guidelines."

      const val HypertensiveCrisisTitle = "Hypertensive Crisis"
      const val HypertensiveCrisisSystolic = "Systolic: Higher than 180"
      const val HypertensiveCrisisDiastolic = "Diastolic: Higher than 120"

      const val HypertensionStage2Title = "Hypertension Stage 2"
      const val HypertensionStage2Systolic = "Systolic: 140 or higher"
      const val HypertensionStage2Diastolic = "Diastolic: 90 or higher"

      const val HypertensionStage1Title = "Hypertension Stage 1"
      const val HypertensionStage1Systolic = "Systolic: 130-139"
      const val HypertensionStage1Diastolic = "Diastolic: 80-89"

      const val ElevatedTitle = "Elevated"
      const val ElevatedSystolic = "Systolic: 120-129"
      const val ElevatedDiastolic = "Diastolic: Less than 80"

      const val NormalTitle = "Normal"
      const val NormalSystolic = "Systolic: Less than 120"
      const val NormalDiastolic = "Diastolic: Less than 80"
    }
  }

  object Baby {
    object CdcPercentiles {
      const val Title = "CDC Growth Percentiles"
      const val SectionTitle = "Understanding Growth Percentiles"
      const val SectionBody = "CDC charts show how a child\u2019s height and weight compare to others the same age. A percentile means what percentage of children measure below that number."
      const val Inches = "inches"
      const val Lbs = "lbs"
      const val Oz = "oz"
      const val Percent = "%"
      const val Placeholder = "--"
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
