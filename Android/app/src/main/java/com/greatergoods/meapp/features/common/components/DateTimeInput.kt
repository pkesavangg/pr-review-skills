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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Represents the value for DateTimeInput.
 */
sealed class DateTimeValue {
    data class Date(
        val millis: Long,
    ) : DateTimeValue()

    data class Time(
        val hour: Int,
        val minute: Int,
    ) : DateTimeValue()

    data class DateTime(
        val millis: Long,
        val hour: Int,
        val minute: Int,
    ) : DateTimeValue()

    fun getString(): String =
        when (this) {
            is Date -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(java.util.Date(millis))
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
                val date = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(java.util.Date(millis))
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

    fun getDateString(): String =
        when (this) {
            is Date,
            -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(java.util.Date(millis))

            is DateTime,
            -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(java.util.Date(millis))

            else -> ""
        }

    fun getTimeString(): String =
        when (this) {
            is Time,
            ->
                SimpleDateFormat("hh:mm a", Locale.getDefault()).format(
                    Calendar
                        .getInstance()
                        .apply {
                            set(Calendar.HOUR_OF_DAY, hour)
                            set(Calendar.MINUTE, minute)
                        }.time,
                )

            is DateTime,
            ->
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
 */
enum class DateTimeInputMode {
    Date,
    Time,
    DateTime,
}

object DateTimeInputDefaults {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun gerDatePickerColor(): DatePickerColors =
        DatePickerDefaults.colors(
            containerColor = MeAppTheme.colorScheme.primary,
            titleContentColor = MeAppTheme.colorScheme.heading,
            dayContentColor = MeAppTheme.colorScheme.body,
            weekdayContentColor = MeAppTheme.colorScheme.body,
            selectedDayContentColor = MeAppTheme.colorScheme.inverse,
            selectedDayContainerColor = MeAppTheme.colorScheme.primaryAction,
            todayContentColor = MeAppTheme.colorScheme.primaryAction,
            todayDateBorderColor = MeAppTheme.colorScheme.primaryAction,
            dividerColor = MeAppTheme.colorScheme.utility,
            navigationContentColor = MeAppTheme.colorScheme.primaryAction,
            yearContentColor = MeAppTheme.colorScheme.body,
            currentYearContentColor = MeAppTheme.colorScheme.body,
            selectedYearContentColor = MeAppTheme.colorScheme.body,
            headlineContentColor = MeAppTheme.colorScheme.body,
            dateTextFieldColors =
                TextFieldDefaults.colors(
                    focusedTextColor = MeAppTheme.colorScheme.primaryAction,
                ),
        )

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun getTimePickerColor(): TimePickerColors =
        TimePickerDefaults.colors(
            clockDialColor = MeAppTheme.colorScheme.secondary,
            clockDialSelectedContentColor = MeAppTheme.colorScheme.inverse,
            clockDialUnselectedContentColor = MeAppTheme.colorScheme.body,
            selectorColor = MeAppTheme.colorScheme.primaryAction,
            periodSelectorBorderColor = MeAppTheme.colorScheme.utility,
            periodSelectorSelectedContainerColor = MeAppTheme.colorScheme.toastBackground,
            periodSelectorUnselectedContainerColor = MeAppTheme.colorScheme.secondary,
            periodSelectorSelectedContentColor = MeAppTheme.colorScheme.primaryAction,
            periodSelectorUnselectedContentColor = MeAppTheme.colorScheme.body,
            timeSelectorSelectedContainerColor = MeAppTheme.colorScheme.toastBackground,
            timeSelectorUnselectedContainerColor = MeAppTheme.colorScheme.secondary,
            timeSelectorSelectedContentColor = MeAppTheme.colorScheme.primaryAction,
            timeSelectorUnselectedContentColor = MeAppTheme.colorScheme.body,
            containerColor = MeAppTheme.colorScheme.primary,
        )

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
    var isDateDialogOpen by remember { mutableStateOf(false) }
    var isTimeDialogOpen by remember { mutableStateOf(false) }
    val currentValue = formControl?.value ?: value
    var localState by remember { mutableStateOf(currentValue ?: DateTimeInputDefaults.defaultValueForMode(mode)) }
    val isError = formControl?.error?.isBlank()?.not() == true

    Row {
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

        if (mode == DateTimeInputMode.DateTime) {
            Spacer(Modifier.width(MeAppTheme.spacing.xs))
        }

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
                    if (mode === DateTimeInputMode.Date) {
                        DateTimeValue.Date(millis)
                    } else {
                        DateTimeValue.DateTime(
                            millis,
                            (localState as DateTimeValue.DateTime).hour,
                            (localState as DateTimeValue.DateTime).minute,
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
                    if (mode === DateTimeInputMode.Time) {
                        DateTimeValue.Time(hour, minute)
                    } else {
                        DateTimeValue.DateTime(
                            (localState as DateTimeValue.DateTime).millis,
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

    // Error/supporting text
    if (formControl != null && isError) {
        Text(
            formControl.error ?: "",
            color = MeAppTheme.colorScheme.error,
            style = MeAppTheme.typography.body3,
        )
    } else if (supportingText != null) {
        Text(
            supportingText,
            color = MeAppTheme.colorScheme.subheading,
            style = MeAppTheme.typography.body3,
        )
    }
}

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
