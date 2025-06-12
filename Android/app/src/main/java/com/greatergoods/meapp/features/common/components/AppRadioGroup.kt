package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.features.common.model.RadioButtonOption
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeAppTheme.spacing

/**
 * A group of radio buttons using AppRadioButton, supporting a group label and option selection.
 *
 * @param options List of RadioButtonOption to display.
 * @param selectedOptionId The currently selected option's id.
 * @param onOptionSelected Callback when an option is selected.
 * @param modifier Modifier for styling.
 * @param groupLabel Optional label for the group.
 */
@Composable
fun <T> AppRadioGroup(
    options: List<RadioButtonOption<T>>,
    selectedOptionId: T?,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    groupLabel: String? = null
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.xs)
    ) {
        groupLabel?.let {
            Text(
                text = it,
                style = MeAppTheme.typography.subHeading1,
                color = MeAppTheme.colorScheme.subheading,
                modifier = Modifier.padding(bottom = spacing.xs)
            )
        }
        options.forEach { option ->
            AppRadioButton(
                selected = option.id == selectedOptionId,
                onClick = { onOptionSelected(option.id) },
                enabled = option.enabled,
                label = option.label
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
        val myOptions = remember {
            listOf(
                RadioButtonOption(id = "opt1", label = "Option 1"),
                RadioButtonOption(id = "opt2", label = "Option 2"),
                RadioButtonOption(id = "opt3", label = "Option 3 (Disabled)", enabled = false),
                RadioButtonOption(id = "opt4", label = "Option 4")
            )
        }
        AppRadioGroup(
            options = myOptions,
            selectedOptionId = selectedOptionId,
            onOptionSelected = { selectedOptionId = it },
            groupLabel = "Choose an option"
        )
    }
}
