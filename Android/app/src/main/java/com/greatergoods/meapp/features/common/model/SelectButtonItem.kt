package com.greatergoods.meapp.features.common.model

/**
 * Represents a selectable button item in the grid.
 *
 * @param id Unique identifier for the button
 * @param displayValue The value to display (text/number or image)
 * @param emitValue The value to emit when selected
 * @param isSelected Whether this button is currently selected
 */
data class SelectButtonItem(
  val id: String,
  val displayValue: SelectButtonDisplayValue,
  val emitValue: String,
  val isSelected: Boolean = false
)

/**
 * Represents the display value for a select button.
 * Can be either text/number or an image.
 */
sealed class SelectButtonDisplayValue {
  /**
   * Text or number display value.
   *
   * @param text The text to display
   * @param prefix Optional prefix to add before the text (e.g., "U" for "U1", "U2")
   */
  data class Text(val text: String, val prefix: String = "") : SelectButtonDisplayValue()

  /**
   * Image display value.
   *
   * @param imageResId The drawable resource ID for the image
   */
  data class Image(val imageResId: Int) : SelectButtonDisplayValue()

  /**
   * Error code display value (special case for error images).
   *
   * @param errorCode The error code (e.g., "t163", "t204")
   */
  data class ErrorCode(val errorCode: String) : SelectButtonDisplayValue()
}
