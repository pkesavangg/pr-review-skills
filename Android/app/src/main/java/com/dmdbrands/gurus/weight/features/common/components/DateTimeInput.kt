package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DatePickerColors
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePickerColors
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.dmdbrands.gurus.weight.theme.MeTheme.typography
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import android.text.format.DateUtils

/** Display format for selected date: e.g. "June 10, 2024". */
private const val DATE_DISPLAY_PATTERN = "MMMM d, yyyy"

/**
 * Represents the value for DateTimeInput.
 * This sealed class allows for type-safe handling of date, time, and combined date-time values.
 */
@Serializable
sealed class DateTimeValue() {
  /**
   * Represents a date value in milliseconds since epoch.
   */
  @Serializable
  data class Date(
     val millis: Long,
  ) : DateTimeValue()

  /**
   * Represents a time value as hour and minute.
   */
  @Serializable
  data class Time(
    val hour: Int,
    val minute: Int,
     val millis: Long = 0L,
  ) : DateTimeValue()

  /**
   * Represents a combined date and time value.
   */
  @Serializable
  data class DateTime(
     val millis: Long,
    val hour: Int,
    val minute: Int,
  ) : DateTimeValue()

  /**
   * Returns a formatted string representation of the value, depending on the type.
   */
  fun getString(): String =
    when (this) {
      is Date -> SimpleDateFormat(DATE_DISPLAY_PATTERN, Locale.getDefault()).format(java.util.Date(this.millis))
      is Time ->
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(
          Calendar
            .getInstance()
            .apply {
              set(Calendar.HOUR_OF_DAY, hour)
              set(Calendar.MINUTE, minute)
            }.time,
        )

      is DateTime -> {
        val date = SimpleDateFormat(DATE_DISPLAY_PATTERN, Locale.getDefault()).format(java.util.Date(this.millis))
        val time =
          SimpleDateFormat("hh:mm a", Locale.getDefault()).format(
            Calendar
              .getInstance()
              .apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
              }.time,
          )
        "$date $time"
      }
    }

  /**
   * Returns a formatted date string for the value (e.g. "June 10, 2024").
   */
  fun getDateString(): String =
    when (this) {
      is Date -> SimpleDateFormat(DATE_DISPLAY_PATTERN, Locale.getDefault()).format(java.util.Date(this.millis))
      is DateTime -> SimpleDateFormat(DATE_DISPLAY_PATTERN, Locale.getDefault()).format(java.util.Date(this.millis))
      else -> ""
    }

  /**
   * Returns a formatted time string for the value, if applicable.
   */
  fun getTimeString(): String =
    when (this) {
      is Time ->
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(
          Calendar
            .getInstance()
            .apply {
              set(Calendar.HOUR_OF_DAY, hour)
              set(Calendar.MINUTE, minute)
            }.time,
        )

      is DateTime ->
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(
          Calendar
            .getInstance()
            .apply {
              set(Calendar.HOUR_OF_DAY, hour)
              set(Calendar.MINUTE, minute)
            }.time,
        )

      else -> ""
    }

  /**
   * Returns the timestamp in milliseconds for the value.
   */
  fun getTimestamp(): Long =
    when (this) {
      is Date -> millis
      is Time ->
        Calendar
          .getInstance()
          .apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
          }.timeInMillis

      is DateTime -> {
        val now = Calendar.getInstance()
        Calendar.getInstance().apply {
          timeInMillis = millis
          set(Calendar.HOUR_OF_DAY, hour)
          set(Calendar.MINUTE, minute)
          set(Calendar.SECOND, now.get(Calendar.SECOND))
          set(Calendar.MILLISECOND, now.get(Calendar.MILLISECOND))
        }.timeInMillis
      }
    }

  companion object {
    /**
     * Converts a date string to epoch milliseconds.
     * @param dateString The date string in YYYY-MM-DD format
     * @param zoneId The time zone to use (defaults to system default)
     * @return The epoch milliseconds, or current time if parsing fails
     */
    fun getEpochMillisFromDateString(dateString: String, zoneId: ZoneId = ZoneId.systemDefault()): Long =
      try {
        LocalDate
          .parse(dateString)
          .atStartOfDay(zoneId)
          .toInstant()
          .toEpochMilli()
      } catch (e: Exception) {
        System.currentTimeMillis()
      }

    /**
     * Formats a timestamp (milliseconds) to YYYY-MM-DD format for API requests.
     * Similar to moment(timestamp).format('Y-MM-DD')
     * @param timestampMillis The timestamp in milliseconds
     * @return The formatted date string in YYYY-MM-DD format
     */
    fun getDateFormatFromMilliseconds(timestampMillis: Long): String =
      try {
        val date = Instant.ofEpochMilli(timestampMillis)
          .atZone(ZoneId.systemDefault())
          .toLocalDate()
        date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
      } catch (e: Exception) {
        ""
      }

    /**
     * Converts a stored date string to epoch milliseconds at local midnight for the calendar
     * date in the string. Accepts either a bare `"yyyy-MM-dd"` date (e.g. the offline-save payload
     * persisted on network failure) or an ISO instant (e.g. the server response for `dob`).
     *
     * For an ISO instant the calendar date is resolved in the **system-default** zone, not UTC.
     * The server persists a date-only field (`dob`) as local-midnight expressed in UTC — e.g. a
     * picked `1999-12-27` in IST comes back as `1999-12-26T18:30:00.000Z`. Reading the UTC date of
     * that instant yields the previous day (Dec 26) for any zone east of UTC; reading it in the
     * device zone recovers the intended wall-clock date (Dec 27). This keeps the round-trip with
     * the local-based [getDateFormatFromMilliseconds] / [getEpochMillisFromDateString] and the
     * date picker stable. (MOB-1499)
     *
     * @param isoString `"yyyy-MM-dd"` or `"YYYY-MM-DDTHH:mm:ss.SSSZ"`.
     * @return The epoch milliseconds, or current time if parsing fails.
     */
    fun getEpochMillisFromIsoString(isoString: String): Long =
      try {
        val localDate = runCatching { LocalDate.parse(isoString) }
          .getOrElse {
            Instant.parse(isoString).atZone(ZoneId.systemDefault()).toLocalDate()
          }
        localDate
          .atStartOfDay(ZoneId.systemDefault())
          .toInstant()
          .toEpochMilli()
      } catch (e: Exception) {
        System.currentTimeMillis()
      }

    /**
     * Formats a timestamp (milliseconds) to an ISO 8601 date-time string for API requests.
     * @param timestampMillis The timestamp in milliseconds.
     * @return The formatted date string in "YYYY-MM-DDTHH:mm:ss.SSSZ" format.
     */
    fun getIsoFormatFromMilliseconds(timestampMillis: Long): String =
      try {
        Instant.ofEpochMilli(timestampMillis).toString()
      } catch (e: Exception) {
        ""
      }
  }
}

/**
 * Enum for DateTimeInput mode.
 * Determines which type of input (date, time, or both) is shown to the user.
 */
enum class DateTimeInputMode {
  Date,
  Time,
  DateTime,
}

/**
 * Default color and value providers for DateTimeInput components.
 */
object DateTimeInputDefaults {
  /**
   * Returns the color scheme for the DatePicker component.
   */
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun getDatePickerColor(): DatePickerColors =
    DatePickerDefaults.colors(
      containerColor = colorScheme.primaryBackground,
      titleContentColor = colorScheme.textHeading,
      dayContentColor = colorScheme.textBody,
      weekdayContentColor = colorScheme.textBody,
      disabledDayContentColor = colorScheme.iconSecondaryDisabled,
      selectedDayContentColor = colorScheme.inverseAction,
      selectedDayContainerColor = colorScheme.primaryAction,
      todayContentColor = colorScheme.primaryAction,
      todayDateBorderColor = colorScheme.primaryAction,
      dividerColor = colorScheme.utility,
      navigationContentColor = colorScheme.primaryAction,
      yearContentColor = colorScheme.textBody,
      currentYearContentColor = colorScheme.textBody,
      disabledYearContentColor = colorScheme.utility,
      selectedYearContentColor = colorScheme.inverseAction,
      selectedYearContainerColor = colorScheme.primaryAction,
      headlineContentColor = colorScheme.textBody,
      dateTextFieldColors =
        TextFieldDefaults.colors(
          errorContainerColor = colorScheme.primaryBackground,
          focusedContainerColor = colorScheme.primaryBackground,
          unfocusedContainerColor = colorScheme.primaryBackground,
          unfocusedTextColor = colorScheme.textBody,
          focusedTextColor = colorScheme.textBody,
          errorTextColor = colorScheme.textBody,
          focusedIndicatorColor = colorScheme.utility,
          unfocusedIndicatorColor = colorScheme.utility,
          cursorColor = colorScheme.iconSecondaryDisabled,
          focusedLabelColor = colorScheme.textBody,
          unfocusedLabelColor = colorScheme.textBody,
        ),
    )

  /**
   * Returns the color scheme for the TimePicker component.
   */
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun getTimePickerColor(): TimePickerColors =
    TimePickerDefaults.colors(
      clockDialColor = colorScheme.secondaryBackground,
      clockDialSelectedContentColor = colorScheme.inverseAction,
      clockDialUnselectedContentColor = colorScheme.textBody,
      selectorColor = colorScheme.primaryAction,
      periodSelectorBorderColor = colorScheme.utility,
      periodSelectorSelectedContainerColor = colorScheme.toastBackground,
      periodSelectorUnselectedContainerColor = colorScheme.secondaryBackground,
      periodSelectorSelectedContentColor = colorScheme.primaryAction,
      periodSelectorUnselectedContentColor = colorScheme.textBody,
      timeSelectorSelectedContainerColor = colorScheme.toastBackground,
      timeSelectorUnselectedContainerColor = colorScheme.secondaryBackground,
      timeSelectorSelectedContentColor = colorScheme.primaryAction,
      timeSelectorUnselectedContentColor = colorScheme.textBody,
      containerColor = colorScheme.primaryBackground,
    )

  /**
   * Returns the default value for a given DateTimeInputMode.
   */
  fun defaultValueForMode(mode: DateTimeInputMode): DateTimeValue {
    val calendar = Calendar.getInstance()
    val currentTimeMillis = calendar.timeInMillis
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val minute = calendar.get(Calendar.MINUTE)
    return when (mode) {
      DateTimeInputMode.Date -> DateTimeValue.Date(currentTimeMillis)
      DateTimeInputMode.Time -> DateTimeValue.Time(hour, minute)
      DateTimeInputMode.DateTime -> DateTimeValue.DateTime(currentTimeMillis, hour, minute)
    }
  }
}

/**
 * A composable input for picking date, time, or both using Android's default dialogs.
 *
 * @param formControl Optional FormControl for value and error handling.
 * @param value Optional stateless value.
 * @param onValueChange Callback for stateless value change.
 * @param mode Whether to pick date, time, or both.
 * @param modifier Modifier for styling.
 * @param label Optional label (not shown inline, for accessibility).
 * @param supportingText Optional supporting text below.
 * @param enabled Whether the input is enabled.
 * @param readOnly Whether the input is read-only.
 * @param minValue Optional minimum value (Date, Time, or DateTime).
 * @param maxValue Optional maximum value (Date, Time, or DateTime).
 */
// Pre-existing long composable (also carried in the detekt baseline before it gained a parameter).
@Suppress("LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeInput(
  modifier: Modifier = Modifier,
  dateTestTag: String? = null,
  timeTestTag: String? = null,
  formControl: FormControl<DateTimeValue>? = null,
  value: DateTimeValue? = null,
  onValueChange: ((DateTimeValue) -> Unit)? = null,
  mode: DateTimeInputMode = DateTimeInputMode.Date,
  label: String? = null,
  supportingText: String? = null,
  enabled: Boolean = true,
  readOnly: Boolean = false,
  minValue: DateTimeValue? = null,
  maxValue: DateTimeValue? = null,
  // When false, disables manual keyboard entry in the date picker (calendar grid only). Set false
  // for Date-of-Birth fields to prevent silent leap-day normalization of typed dates. (MOB-868)
  showModeToggle: Boolean = true,
) {
  // State for dialog visibility
  var isDateDialogOpen by remember { mutableStateOf(false) }
  var isTimeDialogOpen by remember { mutableStateOf(false) }
  // Determine the current value from form control or stateless value
  val currentValue = formControl?.value ?: value
  // Local state for the input value
  var localState by remember { mutableStateOf(currentValue ?: DateTimeInputDefaults.defaultValueForMode(mode)) }
  var isToday by remember { mutableStateOf(DateUtils.isToday(localState.getTimestamp())) }
  // Keep localState in sync with external valuex
  if (currentValue != null && currentValue != localState) {
    localState = currentValue
  }
  // Error state
  val isError = formControl?.isError ?: false
  val minTime = minValue.asTime()
  val maxTime = maxValue.asTime()
  Column {
    if (label != null) {
      Text(
        label,
        color = colorScheme.textHeading,
        style = typography.heading4,
      )
      Spacer(Modifier.height(MeTheme.spacing.sm))
    }
    Row {
      // Show date chip if mode is Date or DateTime
      if (mode == DateTimeInputMode.Date || mode == DateTimeInputMode.DateTime) {
        AppChip(
          label = localState.getDateString(),
          selected = isDateDialogOpen,
          enabled = enabled && !readOnly,
          modifier = if (dateTestTag != null) modifier.testTag(dateTestTag) else modifier,
        ) {
          if (enabled && !readOnly) {
            isDateDialogOpen = true
          }
        }
      }

      // Add spacing between chips if both are shown
      if (mode == DateTimeInputMode.DateTime) {
        Spacer(Modifier.width(MeTheme.spacing.xs))
      }

      // Show time chip if mode is Time or DateTime
      if (mode == DateTimeInputMode.Time || mode == DateTimeInputMode.DateTime) {
        AppChip(
          label = localState.getTimeString(),
          selected = isTimeDialogOpen,
          enabled = enabled && !readOnly,
          modifier = if (timeTestTag != null) modifier.testTag(timeTestTag) else modifier,
        ) {
          if (enabled && !readOnly) {
            isTimeDialogOpen = true
          }
        }
      }
    }
  }

  // Show date picker dialog if needed
  if (isDateDialogOpen) {
    DatePickerDialogContent(
      initialMillis =
        (localState as? DateTimeValue.Date)?.millis
          ?: (localState as? DateTimeValue.DateTime)?.millis
          ?: System.currentTimeMillis(),
      onCancel = { isDateDialogOpen = false },
      hasError = isError,
      onOk = { millis ->
        isToday = DateUtils.isToday(millis)
        isDateDialogOpen = false
        val newValue =
          if (mode == DateTimeInputMode.Date) {
            DateTimeValue.Date(millis)
          } else {
            val dateTime = localState as? DateTimeValue.DateTime
            val (clampedHour, clampedMinute) = if (isToday && dateTime != null) clampTime(
              dateTime.hour,
              dateTime.minute,
              minTime,
              maxTime,
            ) else Pair(
              dateTime?.hour ?: 12,
              dateTime?.minute ?: 0,
            )
            DateTimeValue.DateTime(
              millis,
              clampedHour,
              clampedMinute,
            )
          }
        if (formControl != null) {
          formControl.onValueChange(newValue)
        } else {
          onValueChange?.invoke(newValue)
        }
        localState = newValue
      },
      minValue = minValue,
      maxValue = maxValue,
      showModeToggle = showModeToggle,
    )
  }
  // Show time picker dialog if needed
  if (isTimeDialogOpen) {
    TimePickerDialogContent(
      initial =
        (localState as? DateTimeValue.Time)
          ?: (localState as? DateTimeValue.DateTime)?.let {
            DateTimeValue.Time(
              it.hour,
              it.minute,
            )
          },
      isToday = isToday,
      onCancel = { isTimeDialogOpen = false },
      hasError = isError,
      onOk = { hour, minute ->
        isTimeDialogOpen = false
        val (clampedHour, clampedMinute) = if (isToday) clampTime(hour, minute, minTime, maxTime) else Pair(
          hour,
          minute,
        )
        val newValue =
          if (mode == DateTimeInputMode.Time) {
            DateTimeValue.Time(clampedHour, clampedMinute)
          } else {
            val dateTime = localState as? DateTimeValue.DateTime
            DateTimeValue.DateTime(
              dateTime?.millis ?: System.currentTimeMillis(),
              clampedHour,
              clampedMinute,
            )
          }
        if (formControl != null) {
          formControl.onValueChange(newValue)
        } else {
          onValueChange?.invoke(newValue)
        }
        localState = newValue
      },
      minValue = minValue,
      maxValue = maxValue,
    )
  }

  // Show error or supporting text
  if (formControl != null && isError) {
    val errorMessage = formControl.error?.message ?: ""
    Text(
      errorMessage,
      color = colorScheme.textError,
      style = typography.body3,
    )
  } else if (supportingText != null) {
    Text(
      supportingText,
      color = colorScheme.textSubheading,
      style = typography.body3,
    )
  }
}

/**
 * Preview for DateTimeInput composable in different modes and states.
 */
@OptIn(ExperimentalMaterial3Api::class)
@PreviewTheme
@Composable
fun DateTimeInputPreview() {
  MeAppTheme {
    Column(Modifier.fillMaxSize()) {
      val dateControl =
        remember {
          FormControl.create<DateTimeValue>(
            DateTimeValue.Date(System.currentTimeMillis()),
            emptyList(),
          )
        }
      val timeControl =
        remember { FormControl.create<DateTimeValue>(DateTimeValue.Time(14, 30), emptyList()) }
      val dateTimeControl =
        remember {
          FormControl.create<DateTimeValue>(
            DateTimeValue.DateTime(System.currentTimeMillis(), 9, 15),
            emptyList(),
          )
        }
      DateTimeInput(formControl = dateControl, mode = DateTimeInputMode.Date)
      DateTimeInput(formControl = timeControl, mode = DateTimeInputMode.Time)
      DateTimeInput(formControl = dateTimeControl, mode = DateTimeInputMode.DateTime)
      DateTimeInput(
        value = DateTimeValue.Date(System.currentTimeMillis()),
        onValueChange = {},
        mode = DateTimeInputMode.Date,
      )
      DateTimeInput(value = DateTimeValue.Time(10, 45), onValueChange = {}, mode = DateTimeInputMode.Time)
      DateTimeInput(
        label = "Label",
        value = DateTimeValue.DateTime(System.currentTimeMillis(), 16, 0),
        onValueChange = {},
        mode = DateTimeInputMode.DateTime,
      )
    }
  }
}

fun DateTimeValue?.asMillis(): Long? =
  when (this) {
    is DateTimeValue.Date -> this.millis
    is DateTimeValue.DateTime -> this.millis
    else -> null
  }

fun DateTimeValue?.asTime(): DateTimeValue.Time? =
  when (this) {
    is DateTimeValue.Time -> this
    is DateTimeValue.DateTime -> DateTimeValue.Time(this.hour, this.minute)
    else -> null
  }

fun clampTime(
  hour: Int,
  minute: Int,
  min: DateTimeValue.Time?,
  max: DateTimeValue.Time?,
): Pair<Int, Int> {
  val calendar = Calendar.getInstance()

  val max = max ?: DateTimeValue.Time(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
  var h = hour
  var m = minute
  min?.let {
    if (h < it.hour || (h == it.hour && m < it.minute)) {
      h = it.hour
      m = it.minute
    }
  }
  max?.let {
    if (h > it.hour || (h == it.hour && m > it.minute)) {
      h = it.hour
      m = it.minute
    }
  }
  return h to m
}
