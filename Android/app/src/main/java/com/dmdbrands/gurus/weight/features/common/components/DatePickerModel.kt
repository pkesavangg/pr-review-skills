package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerFormatter
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.features.common.model.ActionButton
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import java.util.Calendar

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
    1992 // Default minimum year
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialogContent(
  initialMillis: Long,
  onCancel: () -> Unit,
  onOk: (Long) -> Unit,
  minValue: DateTimeValue? = null,
  maxValue: DateTimeValue? = null,
) {
  val minDateMillis = minValue.asMillis()
  val maxDateMillis = maxValue.asMillis()
  val yearRange = calculateYearRange(minDateMillis, maxDateMillis)
  val datePickerState =
    rememberDatePickerState(
      initialSelectedDateMillis = initialMillis,
      yearRange = 1992..2100,
      selectableDates =
        object : SelectableDates {
          override fun isSelectableDate(utcTimeMillis: Long): Boolean =
            (minDateMillis == null || utcTimeMillis >= minDateMillis) &&
              (utcTimeMillis <= (maxDateMillis ?: Calendar.getInstance().timeInMillis))

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
          onClick = {
            datePickerState.selectedDateMillis?.let {
              if ((minDateMillis == null || it >= minDateMillis) &&
                (maxDateMillis == null || it <= maxDateMillis)
              ) {
                onOk(it)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerModal(
  onCancel: () -> Unit,
  onOk: (Long) -> Unit,
  modifier: Modifier = Modifier,
  value: Long = System.currentTimeMillis(),
  minValue: DateTimeValue? = null,
  maxValue: DateTimeValue? = null,
) {
  val minDateMillis = minValue.asMillis()
  val maxDateMillis = maxValue.asMillis()
  val yearRange = calculateYearRange(minDateMillis, maxDateMillis)
  val datePickerState =
    rememberDatePickerState(
      initialSelectedDateMillis = value,
      yearRange = yearRange,
      selectableDates =
        object : SelectableDates {
          override fun isSelectableDate(utcTimeMillis: Long): Boolean =
            (minDateMillis == null || utcTimeMillis >= minDateMillis) &&
              (maxDateMillis == null || utcTimeMillis <= maxDateMillis)

          override fun isSelectableYear(year: Int): Boolean {
            return year in 1992..2100
          }
        },
    )
  val pickerColor = DateTimeInputDefaults.getDatePickerColor()
  val dateFormatter: DatePickerFormatter =
    remember { DatePickerDefaults.dateFormatter(selectedDateDescriptionSkeleton = "MMM dd yyyy") }

  BaseModal(
    title = "Height", // TODO: Use string resource
    primaryAction =
      ActionButton(
        text = "OK", // TODO: Use string resource
        action = {
          val selected = datePickerState.selectedDateMillis ?: value
          if ((minDateMillis == null || selected >= minDateMillis) &&
            (maxDateMillis == null || selected <= maxDateMillis)
          ) {
            onOk(selected)
          }
        },
      ),
    secondaryAction = ActionButton(text = "Cancel", action = { onCancel() }), // TODO: Use string resource
  ) {
    DatePicker(
      datePickerState,
      colors = pickerColor,
      dateFormatter = dateFormatter,
    )
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
