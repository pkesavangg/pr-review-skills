package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeAppTheme.colorScheme
import com.greatergoods.meapp.theme.MeAppTheme.spacing
import com.greatergoods.meapp.theme.MeAppTheme.typography

sealed class HeightInput {
    data class Cm(
        val value: Int,
    ) : HeightInput()

    data class FtIn(
        val feet: Int,
        val inches: Int,
    ) : HeightInput()
}

/**
 * Default values for AppPicker height pickers.
 */
object AppPickerDefaults {
    /**
     * List of height values in centimeters (150 to 200 cm).
     */
    val cmHeights: List<HeightInput.Cm> = (150..200).map { HeightInput.Cm(it) }.toList()

    /**
     * List of height values in feet/inches (4'0" to 7'0").
     * Each entry is a Pair of feet to inches (0-11).
     */
    val feetHeights: List<Int> = (4..7).toList()
    val inchHeights: List<Int> = (0..11).toList()
}

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
    value: HeightInput,
    onCancel: () -> Unit,
    onOk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberPickerState(value)
    val itemHeight = (spacing.sm * 2) + 24.dp
    Surface(
        modifier =
            modifier
                .padding(16.dp)
                .fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colorScheme.inverse,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Height",
                style = typography.heading2,
                color = colorScheme.heading,
                modifier = Modifier.padding(bottom = spacing.md),
            )
            when (value) {
                is HeightInput.Cm -> {
                    AppPicker(
                        items = AppPickerDefaults.cmHeights,
                        selectedItem = state.item,
                        labelMapper = { it, _ -> "${(it as HeightInput.Cm).value} cm" },
                        onItemSelected = { state.setItem(it) },
                        itemHeight = itemHeight,
                    )
                }

                else -> {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppPicker(
                            items = AppPickerDefaults.feetHeights,
                            selectedItem = (state.item as HeightInput.FtIn).feet,
                            labelMapper = { it, _ -> "$it'" },
                            onItemSelected = { state.setItem((state.item as HeightInput.FtIn).copy(feet = it)) },
                            modifier = Modifier.weight(1f),
                            itemHeight = itemHeight,
                        )
                        Spacer(modifier = Modifier.padding(horizontal = spacing.sm))
                        AppPicker(
                            items = AppPickerDefaults.inchHeights,
                            selectedItem = (state.item as HeightInput.FtIn).inches,
                            labelMapper = { it, _ -> "$it\"" },
                            onItemSelected = { state.setItem((state.item as HeightInput.FtIn).copy(inches = it)) },
                            modifier = Modifier.weight(1f),
                            itemHeight = itemHeight,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(spacing.lg))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "CANCEL",
                    style = typography.button2,
                    color = colorScheme.subheading,
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(end = spacing.md)
                            .align(Alignment.CenterVertically)
                            .background(Color.Transparent)
                            .padding(vertical = spacing.sm)
                            .clickable { onCancel() },
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Text(
                    text = "OK",
                    style = typography.button2.copy(fontWeight = FontWeight.Bold),
                    color = colorScheme.primaryAction,
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(start = spacing.md)
                            .align(Alignment.CenterVertically)
                            .background(Color.Transparent)
                            .padding(vertical = spacing.sm)
                            .clickable { onOk() },
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@PreviewTheme
@Composable
fun AppHeightPickerModalFeetPreview() {
    MeAppTheme {
        Column {
            AppHeightPickerModal(
                value = HeightInput.Cm(100),
                onCancel = {},
                onOk = {},
            )
            AppHeightPickerModal(
                value = HeightInput.FtIn(feet = 5, inches = 7),
                onCancel = {},
                onOk = {},
            )
        }
    }
}
