package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import com.greatergoods.meapp.theme.MeTheme.spacing

/**
 * Data class representing a radio button option for use in AppRadioGroup.
 *
 * @param id Unique identifier for the option.
 * @param label Display label for the option.
 * @param enabled Whether the option is enabled.
 */
data class RadioButtonOption<T>(
    val id: T,
    val label: String,
    val enabled: Boolean = true,
)

/**
 * A group of radio buttons using AppRadioButton, supporting a group label and option selection.
 *
 * @param options List of RadioButtonOption to display.
 * @param selectedItem The currently selected option's id.
 * @param onOptionSelected Callback when an option is selected.
 * @param modifier Modifier for styling.
 * @param groupLabel Optional label for the group.
 */
@Composable
fun <T> AppRadioGroup(
    options: List<RadioButtonOption<T>>,
    selectedItem: T?,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    groupLabel: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        groupLabel?.let {
            Text(
                text = it,
                style = MeTheme.typography.subHeading1,
                color = MeTheme.colorScheme.textSubheading,
                modifier = Modifier.padding(bottom = spacing.xs),
            )
        }
        options.forEach { option ->
            AppRadioButton(
                selected = option.id == selectedItem,
                onClick = { onOptionSelected(option.id) },
                enabled = option.enabled,
                label = option.label,
            )
        }
    }
}

/**
 * Preview for AppRadioGroup with multiple options and state.
 */
@PreviewTheme
@Composable
fun AppRadioGroupPreview() {
    MeAppTheme {
        var selectedOptionId by remember { mutableStateOf("opt1") }
        val myOptions =
            remember {
                listOf(
                    RadioButtonOption(id = "opt1", label = "Option 1"),
                    RadioButtonOption(id = "opt2", label = "Option 2"),
                    RadioButtonOption(id = "opt3", label = "Option 3 (Disabled)", enabled = false),
                    RadioButtonOption(id = "opt4", label = "Option 4"),
                )
            }
        AppRadioGroup(
            options = myOptions,
            selectedItem = selectedOptionId,
            onOptionSelected = { selectedOptionId = it },
            groupLabel = "Choose an option",
        )
    }
}
