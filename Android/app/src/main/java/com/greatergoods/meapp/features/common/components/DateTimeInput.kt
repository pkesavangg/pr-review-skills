package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Represents the value for DateTimeInput.
 * This sealed class allows for type-safe handling of date, time, and combined date-time values.
 */
sealed class DateTimeValue {
    /**
     * Represents a date value in milliseconds since epoch.
     */
    data class Date(
        val millis: Long,
    ) : DateTimeValue()

    /**
     * Represents a time value as hour and minute.
     */
    data class Time(
        val hour: Int,
        val minute: Int,
    ) : DateTimeValue()

    /**
     * Represents a combined date and time value.
     */
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
            is Date -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(java.util.Date(this.millis))
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
                val date = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(java.util.Date(this.millis))
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
     * Returns a formatted date string for the value, if applicable.
     */
    fun getDateString(): String =
        when (this) {
            is Date -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(java.util.Date(this.millis))
            is DateTime -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(java.util.Date(this.millis))
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
            containerColor = MeTheme.colorScheme.primaryBackground,
            titleContentColor = MeTheme.colorScheme.textHeading,
            dayContentColor = MeTheme.colorScheme.textBody,
            weekdayContentColor = MeTheme.colorScheme.textBody,
            selectedDayContentColor = MeTheme.colorScheme.inverseAction,
            selectedDayContainerColor = MeTheme.colorScheme.primaryAction,
            todayContentColor = MeTheme.colorScheme.primaryAction,
            todayDateBorderColor = MeTheme.colorScheme.primaryAction,
            dividerColor = MeTheme.colorScheme.utility,
            navigationContentColor = MeTheme.colorScheme.primaryAction,
            yearContentColor = MeTheme.colorScheme.textBody,
            currentYearContentColor = MeTheme.colorScheme.textBody,
            selectedYearContentColor = MeTheme.colorScheme.textBody,
            headlineContentColor = MeTheme.colorScheme.textBody,
            dateTextFieldColors =
                TextFieldDefaults.colors(
                    focusedTextColor = MeTheme.colorScheme.primaryAction,
                ),
        )

    /**
     * Returns the color scheme for the TimePicker component.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun getTimePickerColor(): TimePickerColors =
        TimePickerDefaults.colors(
            clockDialColor = MeTheme.colorScheme.secondaryBackground,
            clockDialSelectedContentColor = MeTheme.colorScheme.inverseAction,
            clockDialUnselectedContentColor = MeTheme.colorScheme.textBody,
            selectorColor = MeTheme.colorScheme.primaryAction,
            periodSelectorBorderColor = MeTheme.colorScheme.utility,
            periodSelectorSelectedContainerColor = MeTheme.colorScheme.toastBackground,
            periodSelectorUnselectedContainerColor = MeTheme.colorScheme.secondaryBackground,
            periodSelectorSelectedContentColor = MeTheme.colorScheme.primaryAction,
            periodSelectorUnselectedContentColor = MeTheme.colorScheme.textBody,
            timeSelectorSelectedContainerColor = MeTheme.colorScheme.toastBackground,
            timeSelectorUnselectedContainerColor = MeTheme.colorScheme.secondaryBackground,
            timeSelectorSelectedContentColor = MeTheme.colorScheme.primaryAction,
            timeSelectorUnselectedContentColor = MeTheme.colorScheme.textBody,
            containerColor = MeTheme.colorScheme.primaryBackground,
        )

    /**
     * Returns the default value for a given DateTimeInputMode.
     */
    fun defaultValueForMode(mode: DateTimeInputMode): DateTimeValue =
        when (mode) {
            DateTimeInputMode.Date -> DateTimeValue.Date(System.currentTimeMillis())
            DateTimeInputMode.Time -> DateTimeValue.Time(12, 0)
            DateTimeInputMode.DateTime -> DateTimeValue.DateTime(System.currentTimeMillis(), 12, 0)
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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeInput(
    modifier: Modifier = Modifier,
    formControl: FormControl<DateTimeValue>? = null,
    value: DateTimeValue? = null,
    onValueChange: ((DateTimeValue) -> Unit)? = null,
    mode: DateTimeInputMode = DateTimeInputMode.Date,
    label: String? = null,
    supportingText: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
) {
    // State for dialog visibility
    var isDateDialogOpen by remember { mutableStateOf(false) }
    var isTimeDialogOpen by remember { mutableStateOf(false) }
    // Determine the current value from form control or stateless value
    val currentValue = formControl?.value ?: value
    // Local state for the input value
    var localState by remember { mutableStateOf(currentValue ?: DateTimeInputDefaults.defaultValueForMode(mode)) }
    // Keep localState in sync with external value
    if (currentValue != null && currentValue != localState) {
        localState = currentValue
    }
    // Error state
    val isError = formControl?.isError ?: false

    Row {
        // Show date chip if mode is Date or DateTime
        if (mode == DateTimeInputMode.Date || mode == DateTimeInputMode.DateTime) {
            AppChip(
                label = localState.getDateString(),
                selected = isDateDialogOpen,
                enabled = enabled && !readOnly,
                modifier = modifier,
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
                modifier = modifier,
            ) {
                if (enabled && !readOnly) {
                    isTimeDialogOpen = true
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
            onOk = { millis ->
                isDateDialogOpen = false
                val newValue =
                    if (mode == DateTimeInputMode.Date) {
                        DateTimeValue.Date(millis)
                    } else {
                        val dateTime = localState as? DateTimeValue.DateTime
                        DateTimeValue.DateTime(
                            millis,
                            dateTime?.hour ?: 12,
                            dateTime?.minute ?: 0,
                        )
                    }
                if (formControl != null) {
                    formControl.onValueChange(newValue)
                } else {
                    onValueChange?.invoke(newValue)
                }
                localState = newValue
            },
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
            onCancel = { isTimeDialogOpen = false },
            onOk = { hour, minute ->
                isTimeDialogOpen = false
                val newValue =
                    if (mode == DateTimeInputMode.Time) {
                        DateTimeValue.Time(hour, minute)
                    } else {
                        val dateTime = localState as? DateTimeValue.DateTime
                        DateTimeValue.DateTime(
                            dateTime?.millis ?: System.currentTimeMillis(),
                            hour,
                            minute,
                        )
                    }
                if (formControl != null) {
                    formControl.onValueChange(newValue)
                } else {
                    onValueChange?.invoke(newValue)
                }
                localState = newValue
            },
        )
    }

    // Show error or supporting text
    if (formControl != null && isError) {
        val errorMessage = formControl.error?.message ?: ""
        Text(
            errorMessage,
            color = MeAppTheme.colorScheme.error,
            style = MeAppTheme.typography.body3,
        )
    } else if (supportingText != null) {
        Text(
            supportingText,
            color = MeTheme.colorScheme.textSubheading,
            style = MeTheme.typography.body3,
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
            val fakeScope = rememberCoroutineScope()
            val dateControl =
                remember {
                    FormControl<DateTimeValue>(
                        DateTimeValue.Date(System.currentTimeMillis()),
                        emptyList(),
                        emptyList(),
                        fakeScope,
                    )
                }
            val timeControl =
                remember { FormControl<DateTimeValue>(DateTimeValue.Time(14, 30), emptyList(), emptyList(), fakeScope) }
            val dateTimeControl =
                remember {
                    FormControl<DateTimeValue>(
                        DateTimeValue.DateTime(System.currentTimeMillis(), 9, 15),
                        emptyList(),
                        emptyList(),
                        fakeScope,
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
                value = DateTimeValue.DateTime(System.currentTimeMillis(), 16, 0),
                onValueChange = {},
                mode = DateTimeInputMode.DateTime,
            )
        }
    }
}
