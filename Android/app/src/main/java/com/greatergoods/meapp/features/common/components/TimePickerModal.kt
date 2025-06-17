package com.greatergoods.meapp.features.common.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import com.greatergoods.meapp.features.common.model.ActionButton
import com.greatergoods.meapp.theme.MeAppTheme
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialogContent(
    initial: DateTimeValue.Time? = null,
    onCancel: () -> Unit,
    onOk: (Int, Int) -> Unit,
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

    TimePickerDialog(
        timePickerState,
        onDismiss = {
            onCancel()
        },
        onConfirm = {
            onOk(timePickerState.hour, timePickerState.minute)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    timePickerState: TimePickerState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        BaseModal(
            primaryAction =
                ActionButton(
                    text = "OK", // TODO: Use string resource
                    action = {
                        onConfirm()
                    },
                ),
            secondaryAction = ActionButton(text = "Cancel", action = { onDismiss() }), // TODO: Use string resource
        ) {
            val timerColors = DateTimeInputDefaults.getTimePickerColor()
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
