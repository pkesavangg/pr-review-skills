package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerFormatter
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.common.model.ActionButton
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
    val localState = remember { mutableStateOf(value ?: defaultValueForMode(mode)) }
    val currentValue = formControl?.value ?: value ?: localState.value
    val isError = formControl?.error?.isBlank()?.not() == true

    Row {
        if (mode == DateTimeInputMode.Date || mode == DateTimeInputMode.DateTime) {
            AppChip(
                label = currentValue.getDateString(),
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
                label = currentValue.getTimeString(),
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
                (currentValue as? DateTimeValue.Date)?.millis
                    ?: (currentValue as? DateTimeValue.DateTime)?.millis
                    ?: System.currentTimeMillis(),
            onCancel = { isDateDialogOpen = false },
            onOk = { millis ->
                isDateDialogOpen = false
                val newValue = DateTimeValue.Date(millis)
                if (formControl != null) {
                    formControl.onValueChange(newValue)
                } else {
                    localState.value = newValue
                    onValueChange?.invoke(newValue)
                }
            },
        )
    }
    if (isTimeDialogOpen) {
        TimePickerDialogContent(
            initial =
                (currentValue as? DateTimeValue.Time)
                    ?: (currentValue as? DateTimeValue.DateTime)?.let {
                        DateTimeValue.Time(
                            it.hour,
                            it.minute,
                        )
                    },
            onCancel = { isTimeDialogOpen = false },
            onOk = { hour, minute ->
                isTimeDialogOpen = false
                val newValue = DateTimeValue.Time(hour, minute)
                if (formControl != null) {
                    formControl.onValueChange(newValue)
                } else {
                    localState.value = newValue
                    onValueChange?.invoke(newValue)
                }
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

private fun defaultValueForMode(mode: DateTimeInputMode): DateTimeValue =
    when (mode) {
        DateTimeInputMode.Date -> DateTimeValue.Date(System.currentTimeMillis())
        DateTimeInputMode.Time -> DateTimeValue.Time(12, 0)
        DateTimeInputMode.DateTime -> DateTimeValue.DateTime(System.currentTimeMillis(), 12, 0)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialogContent(
    initialMillis: Long,
    onCancel: () -> Unit,
    onOk: (Long) -> Unit,
) {
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            AppButton(
                label = "OK",
                onClick = {
                    datePickerState.selectedDateMillis?.let { onOk(it) }
                },
                type = ButtonType.InlineTextPrimary,
                size = ButtonSize.Small,
            )
        },
        dismissButton = {
            AppButton(
                label = "Cancel",
                onClick = onCancel,
                type = ButtonType.InlineTextTertiary,
                size = ButtonSize.Small,
            )
        },
        colors =
            DatePickerDefaults.colors(
                containerColor = MeAppTheme.colorScheme.primary,
            ),
    ) {
        val pickerColor =
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
) {
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = value)
    val pickerColor =
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
    val dateFormatter: DatePickerFormatter =
        remember { DatePickerDefaults.dateFormatter(selectedDateDescriptionSkeleton = "MMM dd yyyy") }

    BaseModal(
        title = "Height",
        primaryAction =
            ActionButton(
                text = "OK",
                action = {
                    onOk(datePickerState.selectedDateMillis ?: value)
                },
            ),
        secondaryAction = ActionButton(text = "Cancel", action = { onCancel() }),
    ) {
        DatePicker(
            datePickerState,
            colors = pickerColor,
            dateFormatter = dateFormatter,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialogContent(
    initial: DateTimeValue.Time? = null,
    onCancel: () -> Unit,
    onOk: (Int, Int) -> Unit,
) {
    val openDialog = remember { mutableStateOf(true) }
    if (openDialog.value) {
        val currentTime = Calendar.getInstance()
        val hour = initial?.hour ?: currentTime.get(Calendar.HOUR_OF_DAY)
        val minute = initial?.minute ?: currentTime.get(Calendar.MINUTE)
        val timePickerState =
            rememberTimePickerState(
                initialHour = hour,
                initialMinute = minute,
                is24Hour = false, // Use 12-hour format with AM/PM
            )

        TimePickerDialog(
            onDismiss = {
                onCancel()
            },
            onConfirm = {
                onOk(timePickerState.hour, timePickerState.minute)
            },
        ) {
            val timerColors =
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

            TimePicker(
                state = timePickerState,
                colors = timerColors,
            )
        }
    }
}

@Composable
fun TimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        BaseModal(
            primaryAction =
                ActionButton(
                    text = "OK",
                    action = {
                        onConfirm()
                    },
                ),
            secondaryAction = ActionButton(text = "Cancel", action = { onDismiss() }),
        ) {
            content()
        }
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
