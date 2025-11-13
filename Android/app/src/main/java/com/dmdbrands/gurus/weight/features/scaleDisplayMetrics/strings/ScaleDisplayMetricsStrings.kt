package com.dmdbrands.gurus.weight.features.scaleDisplayMetrics.strings

object ScaleDisplayMetricsStrings {
  const val Title = "Display Metrics"
  const val Description = "Choose which metrics are seen on the scale and the order in which they'll appear."
  const val Save = "SAVE"
  const val LoaderMessage = "Saving..."

  object Toast {
    const val Success = "Scale mode preference updated."
    const val Error = "Error updating scale mode preference."
  }

  object UpdateAccountFailedAlert {
    const val Title = "Update Failed"
    const val Message = "Failed to update scale settings. Please try again."
    const val Retry = "Retry"
    const val Cancel = "Cancel"
  }

  object WeightOnlyModeNotes {
    const val Title = "A user has Weight Only Mode on"
    const val Message = "You can temporarily enable All Body Metrics and/or review users from scale settings."
  }
}
