package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.model.ActionButton
import com.dmdbrands.gurus.weight.theme.MeAppTheme
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
      is Cm -> kotlin.math.round(value / CM_PER_STORED_UNIT).toInt()

      is FtIn -> {
        val totalInches = (feet * INCHES_PER_FOOT) + inches
        totalInches * STORED_HEIGHT_TO_INCHES_FACTOR
      }
    }

  companion object {
    /**
     * Imperial height bounds. The ft/in picker tops out at 7'11", which is the
     * realistic maximum; any taller metric value (the cm picker allows up to
     * 299 cm) is capped to 7'11" when converting to imperial so the displayed
     * value and the picker always agree (MOB-172). Clamp on the TOTAL inches
     * rather than feet/inches independently, otherwise an out-of-range value
     * like 9'10" would clamp to a nonsensical 7'10".
     */
    const val MIN_FEET = 2
    const val MAX_FEET = 7
    private const val INCHES_PER_FOOT = 12
    const val MIN_TOTAL_INCHES = MIN_FEET * INCHES_PER_FOOT // 2'0"
    const val MAX_TOTAL_INCHES = MAX_FEET * INCHES_PER_FOOT + 11 // 7'11"

    /**
     * Metric (cm) picker bounds. The cm picker shows 100..299 cm, so any
     * converted metric height is coerced into this range before display.
     */
    const val MIN_CM = 100
    const val MAX_CM = 299

    /** Centimetres per inch — the metric/imperial conversion factor. */
    private const val CM_PER_INCH = 2.54

    /** Stored height is expressed in tenths of an inch. */
    private const val STORED_HEIGHT_TO_INCHES_FACTOR = 10

    /** Centimetres per stored-height unit (= [CM_PER_INCH] / [STORED_HEIGHT_TO_INCHES_FACTOR]). */
    private const val CM_PER_STORED_UNIT = 0.254

    /** Converts a total-inches value into a feet/inches pair, capped to [2'0", 7'11"]. */
    private fun totalInchesToFtIn(totalInches: Int): FtIn {
      val capped = totalInches.coerceIn(MIN_TOTAL_INCHES, MAX_TOTAL_INCHES)
      return FtIn(feet = capped / INCHES_PER_FOOT, inches = capped % INCHES_PER_FOOT)
    }

    /**
     * Converts a centimetre value to feet/inches, capped to 7'11". Used when the
     * user toggles units so a tall metric height (the cm picker allows up to
     * 299 cm) is shown as the 7'11" maximum rather than an out-of-range value.
     */
    fun cmToFtIn(cm: Int): FtIn = totalInchesToFtIn(kotlin.math.round(cm / CM_PER_INCH).toInt())

    /**
     * Converts a feet/inches value to centimetres, coerced into the cm picker's
     * displayable range [[MIN_CM], [MAX_CM]]. Symmetric to [cmToFtIn]; used when
     * the user toggles to metric so a short imperial height (the ft/in picker
     * allows down to 2'0" ≈ 61 cm) is shown as the 100 cm minimum rather than an
     * out-of-range value the cm picker can't display (MOB-172).
     */
    fun ftInToCm(feet: Int, inches: Int): Cm {
      val totalInches = (feet * INCHES_PER_FOOT) + inches
      val cm = kotlin.math.round(totalInches * CM_PER_INCH).toInt()
      return Cm(cm.coerceIn(MIN_CM, MAX_CM))
    }

    /**
     * Creates a HeightInput from stored height format based on unit preference.
     * @param storedHeight Height in stored format (tenths of inches)
     * @param isMetric Whether to create metric (cm) or imperial (ft/in) input
     * @return HeightInput in the appropriate format
     */
    fun fromStoredHeight(storedHeight: Int, isMetric: Boolean): HeightInput {
      return if (isMetric) {
        // Convert stored height to cm, coerced into the cm picker's range.
        val heightInCm = kotlin.math.round(storedHeight * CM_PER_STORED_UNIT).toInt()
        Cm(heightInCm.coerceIn(MIN_CM, MAX_CM))
      } else {
        // Convert stored height to feet/inches, capped to [2'0", 7'11"].
        totalInchesToFtIn(storedHeight / STORED_HEIGHT_TO_INCHES_FACTOR)
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
        // Convert stored height to cm, coerced into the cm picker's range.
        val heightInCm = kotlin.math.round(height * CM_PER_STORED_UNIT).toInt()
        heightInCm.coerceIn(MIN_CM, MAX_CM).toString() + " cm"
      } else {
        // Convert stored height to feet/inches, capped to [2'0", 7'11"].
        val ftIn = totalInchesToFtIn(height / STORED_HEIGHT_TO_INCHES_FACTOR)
        "${ftIn.feet}' ${ftIn.inches}\""
      }
    }
  }
}

/**
 * Default values for AppPicker height pickers.
 */
object AppPickerDefaults {
  /**
   * List of height values in centimeters (100 to 299 cm).
   */
  val cmHeights: List<HeightInput.Cm> =
    (HeightInput.MIN_CM..HeightInput.MAX_CM).map { HeightInput.Cm(it) }.toList()

  /**
   * List of feet values (2'..7'). Taller metric heights are capped to 7'11"
   * on conversion so the picker never receives an out-of-range feet value
   * (MOB-172).
   */
  val feetHeights: List<Int> = (HeightInput.MIN_FEET..HeightInput.MAX_FEET).toList()
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
  confirmText: String = "ok"
) {
  val state = rememberPickerState(value)
  // State to control dialog visibility

  val itemHeight = (spacing.sm * 2) + 24.dp
          BaseModal(
            title = "Height",
            primaryAction =
              ActionButton(
                text = confirmText,
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
