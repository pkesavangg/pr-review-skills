package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerFormatter
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private val datePickerFormatter = object : DatePickerFormatter {
  override fun formatDate(dateMillis: Long?, locale: Locale, forContentDescription: Boolean): String? =
    dateMillis?.let { SimpleDateFormat("EEE, MMM d", locale).format(java.util.Date(DatePickerDateConstraints.utcDateMillisToLocalMillis(it))) }
  override fun formatMonthYear(monthMillis: Long?, locale: Locale): String? =
    monthMillis?.let { SimpleDateFormat("MMMM yyyy", locale).format(java.util.Date(DatePickerDateConstraints.utcDateMillisToLocalMillis(it))) }
}

/**
 * Pure, timezone-aware constraint helpers for the shared date picker.
 *
 * These are the single source of truth for the min/max bounds that gate BOTH calendar selection
 * AND keyboard/manual text entry: Material 3's [DatePicker] validates typed dates through the same
 * [SelectableDates] predicate and year range, so keeping this logic here (and unit-testing it)
 * guarantees the constraints apply identically no matter how the user enters the date. (MOB-1578)
 */
internal object DatePickerDateConstraints {
  /** Default earliest year offered when no minimum date is supplied. */
  const val DEFAULT_MIN_YEAR = 1922

  /**
   * Calculates the year range for the date picker based on min and max values.
   * @param minValue The minimum date value (local millis) or null for the default floor.
   * @param maxValue The maximum date value (local millis) or null for the current year.
   * @param nowMillis Reference "now" used when [maxValue] is null (injectable for tests).
   * @return A range of years from min to max year.
   */
  fun calculateYearRange(
    minValue: Long?,
    maxValue: Long?,
    nowMillis: Long = System.currentTimeMillis(),
  ): IntRange {
    val calendar = Calendar.getInstance()

    val minYear = if (minValue != null) {
      calendar.timeInMillis = minValue
      calendar.get(Calendar.YEAR)
    } else {
      DEFAULT_MIN_YEAR
    }

    val maxYear = if (maxValue != null) {
      calendar.timeInMillis = maxValue
      calendar.get(Calendar.YEAR)
    } else {
      calendar.timeInMillis = nowMillis
      calendar.get(Calendar.YEAR)
    }

    return minYear..maxYear
  }

  /**
   * Converts local time millis to UTC date millis (midnight UTC for the same date in local
   * timezone). Material3 DatePicker expects UTC milliseconds representing dates at midnight UTC.
   */
  fun localMillisToUtcDateMillis(localMillis: Long): Long {
    val localCal = Calendar.getInstance().apply { timeInMillis = localMillis }
    val year = localCal.get(Calendar.YEAR)
    val month = localCal.get(Calendar.MONTH)
    val day = localCal.get(Calendar.DAY_OF_MONTH)

    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
      set(year, month, day, 0, 0, 0)
      set(Calendar.MILLISECOND, 0)
    }
    return utcCal.timeInMillis
  }

  /**
   * Converts UTC date millis (midnight UTC) to local date millis (midnight local for the same date).
   */
  fun utcDateMillisToLocalMillis(utcMillis: Long): Long {
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    val year = utcCal.get(Calendar.YEAR)
    val month = utcCal.get(Calendar.MONTH)
    val day = utcCal.get(Calendar.DAY_OF_MONTH)

    val localCal = Calendar.getInstance().apply {
      set(year, month, day, 0, 0, 0)
      set(Calendar.MILLISECOND, 0)
    }
    return localCal.timeInMillis
  }

  /**
   * Whether the given UTC date is within the selectable bounds. Applied by [SelectableDates] to both
   * calendar taps and typed input. A null minimum is unbounded below; a null maximum defaults to
   * [defaultMaxUtcMillis] (today), matching the picker's "no future dates" behaviour.
   */
  fun isDateSelectable(
    utcTimeMillis: Long,
    minDateMillis: Long?,
    maxDateMillis: Long?,
    defaultMaxUtcMillis: Long,
  ): Boolean =
    (minDateMillis == null || utcTimeMillis >= minDateMillis) &&
      (utcTimeMillis <= (maxDateMillis ?: defaultMaxUtcMillis))

  /**
   * Whether a confirmed date passes the explicit min/max bounds. Unlike [isDateSelectable] a null
   * maximum here is treated as unbounded — this is the final OK-time guard.
   */
  fun isWithinConfirmBounds(
    utcTimeMillis: Long,
    minDateMillis: Long?,
    maxDateMillis: Long?,
  ): Boolean =
    (minDateMillis == null || utcTimeMillis >= minDateMillis) &&
      (maxDateMillis == null || utcTimeMillis <= maxDateMillis)
}

// Pre-existing long composable (also carried in the detekt baseline before it gained a parameter).
@Suppress("LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialogContent(
  initialMillis: Long,
  onCancel: () -> Unit,
  onOk: (Long) -> Unit,
  minValue: DateTimeValue? = null,
  maxValue: DateTimeValue? = null,
  hasError: Boolean = false,
  // Whether to show Material 3's keyboard/text-input toggle so a date can be typed directly in
  // addition to calendar selection. Defaults to true so every date picker in the app offers manual
  // entry (MOB-1578). The min/max bounds in [DatePickerDateConstraints] gate typed input through the
  // same [SelectableDates] predicate, so constraints apply identically for keyboard and calendar.
  // The parameter is retained so a caller can still force grid-only entry when required.
  showModeToggle: Boolean = true,
) {
  val minDateMillis = minValue.asMillis()?.let { DatePickerDateConstraints.localMillisToUtcDateMillis(it) }
  val maxDateMillis = maxValue.asMillis()?.let { DatePickerDateConstraints.localMillisToUtcDateMillis(it) }
  val yearRange = DatePickerDateConstraints.calculateYearRange(minValue.asMillis(), maxValue.asMillis())
  val initialUtcMillis = DatePickerDateConstraints.localMillisToUtcDateMillis(initialMillis)
  val todayUtcMillis =
    DatePickerDateConstraints.localMillisToUtcDateMillis(Calendar.getInstance().timeInMillis)

  val datePickerState =
    rememberDatePickerState(
      initialSelectedDateMillis = initialUtcMillis,
      yearRange = yearRange,
      selectableDates =
        object : SelectableDates {
          override fun isSelectableDate(utcTimeMillis: Long): Boolean =
            DatePickerDateConstraints.isDateSelectable(
              utcTimeMillis = utcTimeMillis,
              minDateMillis = minDateMillis,
              maxDateMillis = maxDateMillis,
              defaultMaxUtcMillis = todayUtcMillis,
            )

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
              val localMillis = DatePickerDateConstraints.utcDateMillisToLocalMillis(utcMillis)
              if (DatePickerDateConstraints.isWithinConfirmBounds(utcMillis, minDateMillis, maxDateMillis)) {
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
    modifier = Modifier.then(
      if(datePickerState.displayMode == DisplayMode.Picker){
        Modifier.fillMaxSize()
      }
      else{
        Modifier.fillMaxSize().imePadding()
      }
    )
  ) {
    val pickerColor = DateTimeInputDefaults.getDatePickerColor()
    CompositionLocalProvider(LocalContentColor provides MeTheme.colorScheme.primaryAction) {
      // TODO(MOB-1578): M3's keyboard (Input mode) parser uses ResolverStyle.SMART and silently
      //  normalizes an impossible typed date (e.g. 02/29 of a non-leap year -> 02/28) instead of
      //  rejecting it. The parse happens inside M3's internal DateInput/CalendarModel, so the app
      //  only receives the already-clamped millis and cannot surface an "Enter a valid date" error
      //  here. Fix requires an app-owned strict-parsing entry field (LocalDate.of / ResolverStyle
      //  .STRICT). Tracked separately; do not remove keyboard entry to work around it.
      DatePicker(
        state = datePickerState,
        colors = pickerColor,
        dateFormatter = datePickerFormatter,
        showModeToggle = showModeToggle,
      )
    }
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
