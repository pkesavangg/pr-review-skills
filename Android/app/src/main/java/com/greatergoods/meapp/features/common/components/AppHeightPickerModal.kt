package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.features.common.model.ActionButton
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeAppTheme.spacing

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
    onCancel: () -> Unit,
    onOk: (HeightInput) -> Unit,
    modifier: Modifier = Modifier,
    value: HeightInput = HeightInput.FtIn(0, 0),
) {
    val state = rememberPickerState(value)
    // State to control dialog visibility

    val itemHeight = (spacing.sm * 2) + 24.dp
    BaseModal(
        title = "Height",
        primaryAction =
            ActionButton(
                text = "OK",
                action = {
                    onOk(state.item)
                },
            ),
        secondaryAction = ActionButton(text = "Cancel", action = { onCancel() }),
    ) {
        Spacer(Modifier.height(MeAppTheme.spacing.xs))
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
                        onItemSelected = {
                            state.setItem(
                                (state.item as HeightInput.FtIn).copy(
                                    inches = it,
                                ),
                            )
                        },
                        modifier = Modifier.weight(1f),
                        itemHeight = itemHeight,
                    )
                }
            }
        }
    }
}

@PreviewTheme
@Composable
fun AppHeightPickerModalPreview() {
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
