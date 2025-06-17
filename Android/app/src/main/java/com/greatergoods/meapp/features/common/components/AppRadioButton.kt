package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeAppTheme.colorScheme
import com.greatergoods.meapp.theme.MeAppTheme.spacing
import com.greatergoods.meapp.theme.MeAppTheme.typography

/**
 * A theme-aware radio button composable using Material3 RadioButton and MeAppTheme tokens.
 * Supports enabled, disabled, selected, and unselected states, with an optional label.
 *
 * @param selected Whether the radio button is selected.
 * @param onClick Click handler (nullable for read-only display).
 * @param modifier Modifier for styling.
 * @param enabled Whether the radio button is enabled.
 * @param label Optional label to display next to the radio button.
 */
@Composable
fun AppRadioButton(
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val radioColors =
        RadioButtonDefaults.colors(
            selectedColor = colorScheme.primaryAction,
            unselectedColor = colorScheme.utility,
            disabledSelectedColor = colorScheme.secondaryDisabled,
            disabledUnselectedColor = colorScheme.secondaryDisabled,
        )
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            enabled = enabled,
            colors = radioColors,
        )
        if (label != null) {
            Spacer(modifier = Modifier.width(spacing.sm))
            Text(
                text = label,
                style = typography.body2,
                color =
                    when {
                        !enabled -> colorScheme.secondaryDisabled
                        else -> colorScheme.body
                    },
            )
        }
    }
}

/**
 * Preview for AppRadioButton in various states and themes.
 */
@PreviewTheme
@Composable
fun AppRadioButtonPreview() {
    MeAppTheme {
        Column(verticalArrangement = Arrangement.spacedBy(MeAppTheme.spacing.md)) {
            AppRadioButton(selected = true, enabled = true, label = "Selected")
            AppRadioButton(selected = false, enabled = true, label = "Unselected")
            AppRadioButton(selected = true, enabled = false, label = "Selected Disabled")
            AppRadioButton(selected = false, enabled = false, label = "Unselected Disabled")
            AppRadioButton(selected = false, enabled = true)
        }
    }
}
