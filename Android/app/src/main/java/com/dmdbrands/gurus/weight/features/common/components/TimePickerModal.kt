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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import com.dmdbrands.gurus.weight.features.common.model.ActionButton
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import kotlinx.coroutines.FlowPreview
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
  val timePickerState = rememberTimePickerState(
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
      val calendar = Calendar.getInstance()
      val nowHour = calendar.get(Calendar.HOUR_OF_DAY)
      val nowMinute = calendar.get(Calendar.MINUTE)
      val effectiveMaxTime = maxTime ?: DateTimeValue.Time(nowHour, nowMinute)
      val (clampedHour, clampedMinute) = clampTime(
        timePickerState.hour,
        timePickerState.minute,
        minTime,
        effectiveMaxTime,
      )
      onOk(clampedHour, clampedMinute)
    },
    minTime = minTime,
    maxTime = maxTime,
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
) {
  var shouldApplyConstraints by remember { mutableStateOf(false) }
  var constraintTriggered by remember { mutableStateOf(false) }

  // Apply constraints when user releases drag or tap
  fun applyTimeConstraints() {
    val calendar = Calendar.getInstance()
    val nowHour = calendar.get(Calendar.HOUR_OF_DAY)
    val nowMinute = calendar.get(Calendar.MINUTE)
    val effectiveMaxTime = maxTime ?: DateTimeValue.Time(nowHour, nowMinute)

    var selectedHour = timePickerState.hour
    var selectedMinute = timePickerState.minute

    // Apply max time constraints
    if (timePickerState.hour > effectiveMaxTime.hour) {
      selectedHour = effectiveMaxTime.hour
      constraintTriggered = true
    } else if (timePickerState.hour >= effectiveMaxTime.hour && timePickerState.minute > effectiveMaxTime.minute) {
      selectedHour = effectiveMaxTime.hour
      selectedMinute = effectiveMaxTime.minute
      constraintTriggered = true
    }

    // Apply min time constraints
    if (minTime != null) {
      if (selectedHour < minTime.hour) {
        selectedHour = minTime.hour
        selectedMinute = minTime.minute
      } else if (selectedHour == minTime.hour && selectedMinute < minTime.minute) {
        selectedMinute = minTime.minute
      }
    }
  }

  // Watch for the trigger variable and apply constraints with delay
  LaunchedEffect(shouldApplyConstraints) {
    if (shouldApplyConstraints) {
      applyTimeConstraints()
      shouldApplyConstraints = false // Reset the trigger
    }
  }


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
      Text(
        "Select time",
        style = MeTheme.typography.heading5,
        modifier = Modifier.padding(bottom = MeTheme.spacing.md),
        color = MeTheme.colorScheme.textHeading,
      )
      TimePicker(
        state = timePickerState,
        colors = timerColors,
        modifier = Modifier.pointerInput(Unit) {
          awaitPointerEventScope {
            while (true) {
              val event = awaitPointerEvent()
              if (event.type == PointerEventType.Release) {
                shouldApplyConstraints = true
              }
            }
          }
        },
      )
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

