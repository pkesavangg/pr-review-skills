package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dmdbrands.gurus.weight.features.common.model.ActionButton
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import kotlinx.serialization.Serializable

@Serializable
sealed class HeightInput {
  @Serializable
  data class Cm(
    val value: Int,
  ) : HeightInput()

  @Serializable
  data class FtIn(
    val feet: Int,
    val inches: Int,
  ) : HeightInput()

  // convert the value to string using getString
  fun getString(): String =
    when (this) {
      is Cm -> "$value cm"
      is FtIn -> "$feet' $inches\""
    }

  /**
   * Converts this HeightInput to stored height format (tenths of inches).
   * @return Height in stored format
   */
  fun toStoredHeight(): Int =
    when (this) {
      is Cm -> {
        // Convert cm to stored height format
        val CM_TO_INCH_FACTOR = 0.254
        kotlin.math.round(value / CM_TO_INCH_FACTOR).toInt()
      }

      is FtIn -> {
        // Convert feet/inches to stored height format
        val INCHES_PER_FOOT = 12
        val STORED_HEIGHT_TO_INCHES_FACTOR = 10
        val totalInches = (feet * INCHES_PER_FOOT) + inches
        (totalInches * STORED_HEIGHT_TO_INCHES_FACTOR).toInt()
      }
    }

  companion object {
    /**
     * Creates a HeightInput from stored height format based on unit preference.
     * @param storedHeight Height in stored format (tenths of inches)
     * @param isMetric Whether to create metric (cm) or imperial (ft/in) input
     * @return HeightInput in the appropriate format
     */
    fun fromStoredHeight(storedHeight: Int, isMetric: Boolean): HeightInput {
      return if (isMetric) {
        // Convert stored height to cm
        val CM_TO_INCH_FACTOR = 0.254
        val heightInCm = kotlin.math.round(storedHeight * CM_TO_INCH_FACTOR).toInt()
        Cm(heightInCm.coerceIn(100, 200)) // Ensure within valid range
      } else {
        // Convert stored height to feet/inches
        val STORED_HEIGHT_TO_INCHES_FACTOR = 10
        val INCHES_PER_FOOT = 12
        val totalInches = storedHeight / STORED_HEIGHT_TO_INCHES_FACTOR
        val feet = totalInches / INCHES_PER_FOOT
        val inches = totalInches % INCHES_PER_FOOT
        FtIn(
          feet = feet.coerceIn(2, 7), // Ensure within valid range
          inches = inches.coerceIn(0, 11),
        )
      }
    }

    /**
     * Formats height display based on unit preference.
     * @param height Height in stored format (tenths of inches)
     * @param isMetric Whether to display in metric (cm) or imperial (ft/in)
     * @return Formatted height string
     */
    fun formatHeightDisplay(height: Int?, isMetric: Boolean): String {
      if (height == null) return "Not Set"
      return if (isMetric) {
        // Convert stored height to cm
        val CM_TO_INCH_FACTOR = 0.254
        val heightInCm = kotlin.math.round(height * CM_TO_INCH_FACTOR).toInt()
        heightInCm.coerceIn(100, 200).toString() + " cm"
      } else {
        // Convert stored height to feet/inches
        val STORED_HEIGHT_TO_INCHES_FACTOR = 10
        val INCHES_PER_FOOT = 12
        val totalInches = height / STORED_HEIGHT_TO_INCHES_FACTOR
        val feet = totalInches / INCHES_PER_FOOT
        val inches = totalInches % INCHES_PER_FOOT
        "$feet' $inches\""
      }
    }
  }
}

/**
 * Default values for AppPicker height pickers.
 */
object AppPickerDefaults {
  /**
   * List of height values in centimeters (150 to 200 cm).
   */
  val cmHeights: List<HeightInput.Cm> = (100..200).map { HeightInput.Cm(it) }.toList()

  /**
   * List of height values in feet/inches (4'0" to 7'0").
   * Each entry is a Pair of feet to inches (0-11).
   */
  val feetHeights: List<Int> = (2..7).toList()
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
  Dialog(
    onDismissRequest = onCancel,
    properties = DialogProperties(
      dismissOnBackPress = true,
      dismissOnClickOutside = true,
      usePlatformDefaultWidth = false,
      decorFitsSystemWindows = false,
    ),
  ) {
    Dialog(
      onDismissRequest = onCancel,
      properties = DialogProperties(
        dismissOnBackPress = true,
        dismissOnClickOutside = true,
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false,
      ),
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(MeTheme.colorScheme.glow),
      ) {
        Box(modifier = Modifier.align(Alignment.Center)) {
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
            Spacer(Modifier.height(spacing.xs))
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
