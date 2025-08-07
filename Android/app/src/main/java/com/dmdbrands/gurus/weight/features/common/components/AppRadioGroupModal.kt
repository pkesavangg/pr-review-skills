package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.features.common.model.ActionButton
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Data class representing the configuration for a radio group modal.
 *
 * @param title The modal title.
 * @param subtitle Optional subtitle for additional context.
 * @param options List of radio button options.
 * @param selectedItem Currently selected option ID.
 * @param confirmText Text for the confirm button.
 * @param cancelText Text for the cancel button.
 * @param maxHeight Maximum height of the modal content.
 */
data class RadioGroupModalConfig<T>(
    val title: String,
    val subtitle: String? = null,
    val options: List<RadioButtonOption<T>>,
    val selectedItem: T?,
    val confirmText: String = "OK",
    val cancelText: String = "Cancel",
    val maxHeight: androidx.compose.ui.unit.Dp? = null,
)

/**
 * Modal dialog containing a radio group for selection using BaseModal pattern.
 * Follows the same structure as HeightPickerModal, DatePickerModal, and PasswordResetModal.
 *
 * @param title The modal title.
 * @param subtitle Optional subtitle for additional context.
 * @param options List of radio button options.
 * @param selectedItem Currently selected option ID.
 * @param onCancel Callback when user cancels the modal.
 * @param onOk Callback when user confirms selection with the selected item.
 * @param modifier Modifier for styling.
 * @param confirmText Custom confirm button text.
 * @param cancelText Custom cancel button text.
 * @param maxHeight Optional maximum height of the scrollable content.
 */
@Composable
fun <T> AppRadioGroupModal(
    title: String,
    options: List<RadioButtonOption<T>>,
    onCancel: () -> Unit,
    onOk: (T?) -> Unit,
    modifier: Modifier = Modifier,
    selectedItem: T? = null,
    subtitle: String? = null,
    confirmText: String = "OK",
    cancelText: String = "Cancel",
    maxHeight: androidx.compose.ui.unit.Dp? = null,
) {
    var currentSelection by remember { mutableStateOf(selectedItem) }

    BaseModal(
        title = title,
        body = subtitle,
        primaryAction =
            ActionButton(
                text = confirmText,
                action = { onOk(currentSelection) },
            ),
        secondaryAction =
            ActionButton(
                text = cancelText,
                action = onCancel,
            ),
        modifier = modifier,
    ) {
        // Add spacing before radio group content
        Spacer(modifier = Modifier.height(MeTheme.spacing.xs))

        // Radio Group Section - auto-sized with optional max height and scrolling
        val radioGroupModifier =
            if (maxHeight != null) {
                Modifier
                    .fillMaxWidth()
                    .height(maxHeight)
                    .verticalScroll(rememberScrollState())
            } else {
                Modifier.fillMaxWidth()
            }

        AppRadioGroup(
            options = options,
            selectedItem = currentSelection,
            onOptionSelected = { selectedId ->
                currentSelection = selectedId
            },
            modifier = radioGroupModifier,
        )
    }
}

/**
 * Overloaded version using RadioGroupModalConfig for complex configurations.
 */
@Composable
fun <T> AppRadioGroupModal(
    config: RadioGroupModalConfig<T>,
    onCancel: () -> Unit,
    onOk: (T?) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppRadioGroupModal(
        title = config.title,
        subtitle = config.subtitle,
        options = config.options,
        selectedItem = config.selectedItem,
        onCancel = onCancel,
        onOk = onOk,
        confirmText = config.confirmText,
        cancelText = config.cancelText,
        maxHeight = config.maxHeight,
        modifier = modifier,
    )
}

/**
 * Extension function to create a DialogModel.Custom for radio group modal.
 * This integrates with the existing dialog service system.
 */
fun <T> createRadioGroupDialog(
    config: RadioGroupModalConfig<T>,
    onConfirm: (T) -> Unit,
    onCancel: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    priority: Int = 1,
): DialogModel.Custom =
    DialogModel.Custom(
        contentKey = DialogType.RadioGroupPicker,
        params =
            mapOf(
                "config" to config,
                "onConfirm" to onConfirm,
                "onCancel" to onCancel,
            ),
        onDismiss = onDismiss,
        onConfirm = { result -> onConfirm(result as T) },
        customPriority = priority,
    )

/**
 * Convenience function to show radio group modal using dialog service.
 *
 * @param dialogService The dialog queue service instance.
 * @param title Modal title.
 * @param options List of radio button options.
 * @param selectedItem Currently selected item.
 * @param onConfirm Callback when selection is confirmed.
 * @param onCancel Optional callback when cancelled.
 * @param subtitle Optional subtitle.
 * @param confirmText Custom confirm button text.
 * @param cancelText Custom cancel button text.
 */
fun <T> showRadioGroupModal(
    dialogService: com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService,
    title: String,
    options: List<RadioButtonOption<T>>,
    selectedItem: T?,
    onConfirm: (T?) -> Unit,
    onCancel: (() -> Unit)? = null,
    subtitle: String? = null,
    confirmText: String = "OK",
    cancelText: String = "Cancel",
) {
    val config =
        RadioGroupModalConfig(
            title = title,
            subtitle = subtitle,
            options = options,
            selectedItem = selectedItem,
            confirmText = confirmText,
            cancelText = cancelText,
        )

    val dialog =
        createRadioGroupDialog(
            config = config,
            onConfirm = {
                onConfirm(it)
            },
            onCancel = {
                onCancel?.invoke()
            },
            onDismiss = {
                onCancel?.invoke()
            },
        )

    dialogService.enqueue(dialog)
}

@PreviewTheme
@Composable
fun AppRadioGroupModalPreview() {
    MeAppTheme {
        val options =
            remember {
                listOf(
                    RadioButtonOption(id = "option1", label = "Option 1"),
                    RadioButtonOption(id = "option2", label = "Option 2"),
                    RadioButtonOption(id = "option3", label = "Option 3"),
                    RadioButtonOption(id = "option4", label = "Option 4 (Disabled)", enabled = false),
                    RadioButtonOption(id = "option5", label = "Option 5"),
                )
            }

        AppRadioGroupModal(
            title = "Select an Option",
            subtitle = "Choose one of the available options below",
            options = options,
            selectedItem = "option2",
            onCancel = { },
            onOk = { },
        )
    }
}
