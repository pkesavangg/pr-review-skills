package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import com.greatergoods.meapp.features.common.model.ActionButton
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialogContent(
    initial: DateTimeValue.Time? = null,
    onCancel: () -> Unit,
    onOk: (Int, Int) -> Unit,
    minValue: DateTimeValue? = null,
    maxValue: DateTimeValue? = null,
) {
    val currentTime = Calendar.getInstance()
    val hour = initial?.hour ?: currentTime.get(Calendar.HOUR_OF_DAY)
    val minute = initial?.minute ?: currentTime.get(Calendar.MINUTE)
    val timePickerState =
        rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = false, // Use 12-hour format with AM/PM
        )

    val minTime = minValue.asTime()
    val maxTime = maxValue.asTime()
    TimePickerDialog(
        timePickerState,
        onDismiss = {
            onCancel()
        },
        onConfirm = {
            val (clampedHour, clampedMinute) = clampTime(timePickerState.hour, timePickerState.minute, minTime, maxTime)
            onOk(clampedHour, clampedMinute)
        },
        minTime = minTime,
        maxTime = maxTime,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    timePickerState: TimePickerState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    minTime: DateTimeValue.Time? = null,
    maxTime: DateTimeValue.Time? = null,
) {
    Dialog(onDismissRequest = onDismiss) {
        BaseModal(
            primaryAction =
                ActionButton(
                    text = "OK", // TODO: Use string resource
                    action = {
                        val (clampedHour, clampedMinute) =
                            clampTime(
                                timePickerState.hour,
                                timePickerState.minute,
                                minTime,
                                maxTime,
                            )
                        // Set the clamped time in the state before confirming
                        if (clampedHour != timePickerState.hour || clampedMinute != timePickerState.minute) {
                            // Not ideal: TimePickerState is not mutable directly, so just call onConfirm with clamped values
                        }
                        onConfirm()
                    },
                ),
            secondaryAction = ActionButton(text = "Cancel", action = { onDismiss() }), // TODO: Use string resource
        ) {
            val timerColors = DateTimeInputDefaults.getTimePickerColor()
            Text(
                "Select time",
                style = MeTheme.typography.heading5,
                modifier = Modifier.padding(bottom = MeTheme.spacing.md),
                color = MeTheme.colorScheme.textHeading,
            )
            TimePicker(state = timePickerState, colors = timerColors)
        }
    }
}

@PreviewTheme
@Composable
fun TimePickerDialogContentPreview() {
    MeAppTheme {
        TimePickerDialogContent(null, {}, { _, _ -> })
    }
}
