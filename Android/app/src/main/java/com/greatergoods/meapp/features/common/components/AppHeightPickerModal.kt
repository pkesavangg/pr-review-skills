package com.greatergoods.meapp.features.common.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.features.common.enum.AppHeightUnit
import com.greatergoods.meapp.features.common.model.PickerState
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeAppTheme.colorScheme
import com.greatergoods.meapp.theme.MeAppTheme.spacing
import com.greatergoods.meapp.theme.MeAppTheme.typography

/**
 * Modal dialog for picking height in either centimeters or feet/inches.
 *
 * @param unit The height unit (CM or FEET).
 * @param cmState PickerState for cm values.
 * @param feetState PickerState for feet values.
 * @param inchState PickerState for inch values.
 * @param onCancel Called when cancel is pressed.
 * @param onOk Called when OK is pressed.
 * @param modifier Modifier for styling.
 */
@Composable
fun AppHeightPickerModal(
    unit: AppHeightUnit,
    cmState: PickerState<Int>,
    feetState: PickerState<Int>,
    inchState: PickerState<Int>,
    onCancel: () -> Unit,
    onOk: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .padding(16.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colorScheme.inverse,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Height",
                style = typography.heading2,
                color = colorScheme.heading,
                modifier = Modifier.padding(bottom = spacing.md)
            )
            if (unit == AppHeightUnit.CM) {
                AppPicker(
                    items = AppPickerDefaults.cmHeights,
                    state = cmState,
                    labelMapper = { "$it cm" },
                    isFocusNeeded = true
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppPicker(
                        items = (4..7).toList(),
                        state = feetState,
                        labelMapper = { "$it'" },
                        isFocusNeeded = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.padding(horizontal = spacing.sm))
                    AppPicker(
                        items = (0..11).toList(),
                        state = inchState,
                        labelMapper = { "$it\"" },
                        isFocusNeeded = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(spacing.lg))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "CANCEL",
                    style = typography.button2,
                    color = colorScheme.subheading,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = spacing.md)
                        .align(Alignment.CenterVertically)
                        .background(Color.Transparent)
                        .padding(vertical = spacing.sm)
                        .clickable { onCancel() },
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Text(
                    text = "OK",
                    style = typography.button2.copy(fontWeight = FontWeight.Bold),
                    color = colorScheme.primaryAction,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = spacing.md)
                        .align(Alignment.CenterVertically)
                        .background(Color.Transparent)
                        .padding(vertical = spacing.sm)
                        .clickable { onOk() },
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@PreviewTheme
@Composable
fun AppHeightPickerModalCmPreview() {
    MeAppTheme {
        val cmState = remember { PickerState(160) }
        val feetState = remember { PickerState(5) }
        val inchState = remember { PickerState(7) }
        AppHeightPickerModal(
            unit = AppHeightUnit.CM,
            cmState = cmState,
            feetState = feetState,
            inchState = inchState,
            onCancel = {},
            onOk = {}
        )
    }
}

@PreviewTheme
@Composable
fun AppHeightPickerModalFeetPreview() {
    MeAppTheme {
        val cmState = remember { PickerState(160) }
        val feetState = remember { PickerState(6) }
        val inchState = remember { PickerState(8) }
        AppHeightPickerModal(
            unit = AppHeightUnit.FEET,
            cmState = cmState,
            feetState = feetState,
            inchState = inchState,
            onCancel = {},
            onOk = {}
        )
    }
}
