package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import java.util.Calendar
import java.util.TimeZone

/**
 * Calculates the year range for the date picker based on min and max values.
 * @param minValue The minimum date value
 * @param maxValue The maximum date value
 * @return A range of years from min to max year
 */
private fun calculateYearRange(minValue: Long?, maxValue: Long?): IntRange {
  val calendar = Calendar.getInstance()

  // Calculate min year
  val minYear = if (minValue != null) {
    calendar.timeInMillis = minValue
    calendar.get(Calendar.YEAR)
  } else {
    1922 // Default minimum year
  }

  // Calculate max year
  val maxYear = if (maxValue != null) {
    calendar.timeInMillis = maxValue
    calendar.get(Calendar.YEAR)
  } else {
    calendar.timeInMillis = System.currentTimeMillis()
    calendar.get(Calendar.YEAR) // Current year
  }

  return minYear..maxYear
}

/**
 * Converts local time millis to UTC date millis (midnight UTC for the same date in local timezone).
 * Material3 DatePicker expects UTC milliseconds representing dates at midnight UTC.
 */
private fun localMillisToUtcDateMillis(localMillis: Long): Long {
  val localCal = Calendar.getInstance().apply { timeInMillis = localMillis }
  val year = localCal.get(Calendar.YEAR)
  val month = localCal.get(Calendar.MONTH)
  val day = localCal.get(Calendar.DAY_OF_MONTH)

  // Create UTC calendar at midnight for the same date
  val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
    set(year, month, day, 0, 0, 0)
    set(Calendar.MILLISECOND, 0)
  }
  return utcCal.timeInMillis
}

/**
 * Converts UTC date millis (midnight UTC) to local date millis (midnight local for the same date).
 */
private fun utcDateMillisToLocalMillis(utcMillis: Long): Long {
  val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
  val year = utcCal.get(Calendar.YEAR)
  val month = utcCal.get(Calendar.MONTH)
  val day = utcCal.get(Calendar.DAY_OF_MONTH)

  // Create local calendar at midnight for the same date
  val localCal = Calendar.getInstance().apply {
    set(year, month, day, 0, 0, 0)
    set(Calendar.MILLISECOND, 0)
  }
  return localCal.timeInMillis
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialogContent(
  initialMillis: Long,
  onCancel: () -> Unit,
  onOk: (Long) -> Unit,
  minValue: DateTimeValue? = null,
  maxValue: DateTimeValue? = null,
  hasError: Boolean = false,
) {
  val minDateMillis = minValue.asMillis()?.let { localMillisToUtcDateMillis(it) }
  val maxDateMillis = maxValue.asMillis()?.let { localMillisToUtcDateMillis(it) }
  val yearRange = calculateYearRange(minValue.asMillis(), maxValue.asMillis())
  val initialUtcMillis = localMillisToUtcDateMillis(initialMillis)

  val datePickerState =
    rememberDatePickerState(
      initialSelectedDateMillis = initialUtcMillis,
      yearRange = 1922..2100,
      selectableDates =
        object : SelectableDates {
          override fun isSelectableDate(utcTimeMillis: Long): Boolean =
            (minDateMillis == null || utcTimeMillis >= minDateMillis) &&
              (utcTimeMillis <= (maxDateMillis ?: localMillisToUtcDateMillis(Calendar.getInstance().timeInMillis)))

          override fun isSelectableYear(year: Int): Boolean {
            return year in yearRange
          }
        },
    )


  DatePickerDialog(
    onDismissRequest = {
      onCancel()
    },
    confirmButton = {
      Column {
        AppButton(
          label = "OK",
          enabled = datePickerState.selectedDateMillis != null && !hasError,
          onClick = {
            datePickerState.selectedDateMillis?.let { utcMillis ->
              // Convert UTC date millis back to local date millis
              val localMillis = utcDateMillisToLocalMillis(utcMillis)
              if ((minDateMillis == null || utcMillis >= minDateMillis) &&
                (maxDateMillis == null || utcMillis <= maxDateMillis)
              ) {
                onOk(localMillis)
              }
            }
          },
          type = ButtonType.InlineTextPrimary,
          size = ButtonSize.Small,
        )
        Spacer(Modifier.height(MeTheme.spacing.md))
      }
    },
    dismissButton = {
      Column {
        AppButton(
          label = "Cancel",
          onClick = onCancel,
          type = ButtonType.InlineTextTertiary,
          size = ButtonSize.Small,
        )
        Spacer(Modifier.height(MeTheme.spacing.xs))
      }
    },
    colors =
      DatePickerDefaults.colors(
        containerColor = MeTheme.colorScheme.primaryBackground,
      ),
  ) {
    val pickerColor = DateTimeInputDefaults.getDatePickerColor()
    DatePicker(state = datePickerState, colors = pickerColor)
  }
}

@PreviewTheme
@Composable
fun DatePickerDialogContentPreview() {
  MeAppTheme {
    DatePickerDialogContent(
      initialMillis = 100L,
      onCancel = {},
      onOk = {},
    )
  }
}
