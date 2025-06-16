package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerFormatter
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.features.common.model.ActionButton
import com.greatergoods.meapp.theme.MeAppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialogContent(
    initialMillis: Long,
    onCancel: () -> Unit,
    onOk: (Long) -> Unit,
) {
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = {
            onCancel()
        },
        confirmButton = {
            Column {
                AppButton(
                    label = "OK",
                    onClick = {
                        datePickerState.selectedDateMillis?.let { onOk(it) }
                    },
                    type = ButtonType.InlineTextPrimary,
                    size = ButtonSize.Small,
                )
                Spacer(Modifier.height(MeAppTheme.spacing.xs))
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
                Spacer(Modifier.height(MeAppTheme.spacing.xs))
            }
        },
        colors =
            DatePickerDefaults.colors(
                containerColor = MeAppTheme.colorScheme.primary,
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
) {
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = value)
    val pickerColor = DateTimeInputDefaults.getDatePickerColor()
    val dateFormatter: DatePickerFormatter =
        remember { DatePickerDefaults.dateFormatter(selectedDateDescriptionSkeleton = "MMM dd yyyy") }

    BaseModal(
        title = "Height", // TODO: Use string resource
        primaryAction =
            ActionButton(
                text = "OK", // TODO: Use string resource
                action = {
                    onOk(datePickerState.selectedDateMillis ?: value)
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
        DatePickerDialogContent(100L, {}) {}
    }
}
