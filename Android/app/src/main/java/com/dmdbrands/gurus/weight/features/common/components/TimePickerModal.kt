package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import com.dmdbrands.gurus.weight.features.common.model.ActionButton
import com.dmdbrands.gurus.weight.core.shared.utilities.testing.exposeTestTagsAsResourceId
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialogContent(
  initial: DateTimeValue.Time? = null,
  onCancel: () -> Unit,
  onOk: (Int, Int) -> Unit,
  minValue: DateTimeValue? = null,
  maxValue: DateTimeValue? = null,
  isToday: Boolean = false,
  hasError: Boolean = false,
) {
  val currentTime = Calendar.getInstance()
  val hour = initial?.hour ?: currentTime.get(Calendar.HOUR_OF_DAY)
  val minute = initial?.minute ?: currentTime.get(Calendar.MINUTE)
  val timePickerState = rememberTimePickerState(
    initialHour = hour,
    initialMinute = minute,
    is24Hour = false, // Use 12-hour format with AM/PM
  )

  val minTime = minValue.asTime()
  val maxTime = maxValue.asTime() ?: DateTimeValue.Time(
    Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
    Calendar.getInstance().get(Calendar.MINUTE),
    59,
  )

  fun onConfirm() = {
    val calendar = Calendar.getInstance()
    val nowHour = calendar.get(Calendar.HOUR_OF_DAY)
    val nowMinute = calendar.get(Calendar.MINUTE)
    val effectiveMaxTime = maxTime ?: DateTimeValue.Time(nowHour, nowMinute)
    val (clampedHour, clampedMinute) = if (isToday) clampTime(
      timePickerState.hour,
      timePickerState.minute,
      minTime,
      effectiveMaxTime,
    ) else Pair(timePickerState.hour, timePickerState.minute)
    onOk(clampedHour, clampedMinute)
  }

  TimePickerDialog(
    timePickerState,
    isToday = isToday,
    onDismiss = {
      onCancel()
    },
    onConfirm = {
      onOk(timePickerState.hour, timePickerState.minute)
    },
    minTime = minTime,
    maxTime = maxTime,
    hasError = hasError,
  )
}

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun TimePickerDialog(
  timePickerState: TimePickerState,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
  minTime: DateTimeValue.Time? = null,
  maxTime: DateTimeValue.Time? = null,
  isToday: Boolean = false,
  hasError: Boolean = false,
) {
  var constraintTriggered by remember { mutableStateOf(false) }
  val timerColors = DateTimeInputDefaults.getTimePickerColor()
  var effectiveMaxTime: DateTimeValue.Time by remember {
    mutableStateOf(
      DateTimeValue.Time(
        maxTime?.hour ?: Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
        maxTime?.minute ?: Calendar.getInstance().get(Calendar.MINUTE),
      ),
    )
  }

  fun changeMaxTime() {
    val calendar = Calendar.getInstance()
    val nowHour = calendar.get(Calendar.HOUR_OF_DAY)
    val nowMinute = calendar.get(Calendar.MINUTE)
    effectiveMaxTime = DateTimeValue.Time(nowHour, nowMinute)
  }

  // Apply constraints when user releases drag or tap
  fun applyTimeConstraints() {
    changeMaxTime()

    val selectedHour = timePickerState.hour
    val selectedMinute = timePickerState.minute

    if (selectedHour > effectiveMaxTime.hour ||
      (selectedHour == effectiveMaxTime.hour && selectedMinute > effectiveMaxTime.minute)
    ) {
      constraintTriggered = true
      return
    }
    if (minTime != null) {
      if (selectedHour < minTime.hour ||
        (selectedHour == minTime.hour && selectedMinute < minTime.minute)
      ) {
        constraintTriggered = true
        return
      }
    }
    constraintTriggered = false
  }

  // Watch for the trigger variable and apply constraints with delay
  LaunchedEffect(Unit) {
    snapshotFlow { timePickerState.hour to timePickerState.minute }
      .debounce(300)
      .collect {
        if (isToday)
          applyTimeConstraints()
      }
  }

  Dialog(onDismissRequest = onDismiss) {
    BaseModal(
      modifier = Modifier.exposeTestTagsAsResourceId(),
      primaryAction =
        ActionButton(
          text = "OK",
          enabled = !constraintTriggered && !hasError,
          action = {
            onConfirm()
          },
        ),
      error = if (constraintTriggered) "Selected time can’t be in the future" else null,
      secondaryAction = ActionButton(text = "Cancel", action = { onDismiss() }),
    ) {
      Text(
        "Select time",
        style = MeTheme.typography.heading6,
        modifier = Modifier.padding(bottom = MeTheme.spacing.md),
        color = MeTheme.colorScheme.textHeading,
      )
      TimePicker(
        state = timePickerState,
        colors = timerColors,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
fun getTimeString(dateTimeValue: DateTimeValue.Time): String {
  val hour = dateTimeValue.hour - 12
  val amPmString = if (dateTimeValue.hour > 12) "pm" else "am"
  val minuteString = if (dateTimeValue.minute < 10) "0${dateTimeValue.minute}" else "${dateTimeValue.minute}"
  val hourString = if (hour < 10) "0$hour" else "$hour"
  return "$hourString:$minuteString $amPmString"
}

@PreviewTheme
@Composable
fun TimePickerDialogContentPreview() {
  MeAppTheme {
    TimePickerDialogContent(null, {}, { _, _ -> })
  }
}

/**
 * Clamps time values to ensure they are within the specified min and max time bounds.
 */
private fun clampTime(
  hour: Int,
  minute: Int,
  minTime: DateTimeValue.Time?,
  maxTime: DateTimeValue.Time,
): Pair<Int, Int> {
  var clampedHour = hour
  var clampedMinute = minute

  // Apply max time constraints
  if (clampedHour > maxTime.hour) {
    clampedHour = maxTime.hour
    clampedMinute = maxTime.minute
  } else if (clampedHour == maxTime.hour && clampedMinute > maxTime.minute) {
    clampedHour = maxTime.hour
    clampedMinute = maxTime.minute
  }

  // Apply min time constraints
  if (minTime != null) {
    if (clampedHour < minTime.hour) {
      clampedHour = minTime.hour
      clampedMinute = minTime.minute
    } else if (clampedHour == minTime.hour && clampedMinute < minTime.minute) {
      clampedMinute = minTime.minute
    }
  }

  return Pair(clampedHour, clampedMinute)
}

