package com.dmdbrands.gurus.weight.features.common.helper

import com.dmdbrands.gurus.weight.features.common.model.SelectButtonDisplayValue
import com.dmdbrands.gurus.weight.features.common.model.SelectButtonItem
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.resources.AppIcons.Setup.ShowWifiModeFilled
import com.dmdbrands.gurus.weight.resources.AppIcons.Setup.ShowWifiModeOutlined

/**
 * Helper object for creating SelectButtonItem lists for different use cases.
 */
object SelectButtonHelper {

  /**
   * Creates a list of SelectButtonItem for user numbers (U1, U2, etc.).
   *
   * @param numbers List of numbers to display
   * @param selectedNumber The currently selected number, if any
   * @return List of SelectButtonItem for user numbers
   */
  fun createUserNumberButtons(
    numbers: List<Int>,
    selectedNumber: Int? = null
  ): List<SelectButtonItem> {
    return numbers.map { number ->
      SelectButtonItem(
        id = "user_$number",
        displayValue = SelectButtonDisplayValue.Text(
          text = number.toString(),
          prefix = "U",
        ),
        emitValue = number.toString(),
        isSelected = number == selectedNumber,
      )
    }
  }

  /**
   * Creates a list of SelectButtonItem for wifi setup modes.
   *
   * @param selectedMode The currently selected mode, if any
   * @return List of SelectButtonItem for wifi modes
   */
  fun createWifiModeButtons(
    selectedMode: String? = null,
    sku: String? = null,
    userNumber: Int? = null
  ): List<SelectButtonItem> {
    val apModeIconFilled0384 =
      if (sku == "0384") AppIcons.Setup.WifiAPModeFilled0384 else AppIcons.Setup.WifiAPModeSelected
    val apModeIconOutlined0384 = if (sku == "0384") AppIcons.Setup.WifiAPModeOutlined0384 else AppIcons.Setup.WifiAPMode
    return listOf(
      SelectButtonItem(
        id = "wifi_smart_connect",
        displayValue =
          if (sku.toString() == "0384") {
            SelectButtonDisplayValue.Gif(
              if (selectedMode == "espTouchWifi") ShowWifiModeFilled(userNumber ?: 1)
              else ShowWifiModeOutlined(userNumber ?: 1),
            )
          } else {
            SelectButtonDisplayValue.Image(
              if (selectedMode == "espTouchWifi") AppIcons.Setup.WifiSmartConnectSelected
              else AppIcons.Setup.WifiSmartConnect,
            )
          },
        isGif = true,
        emitValue = "espTouchWifi",
        isSelected = selectedMode == "espTouchWifi",
      ),
      SelectButtonItem(
        id = "wifi_ap_mode",
        displayValue = SelectButtonDisplayValue.Image(
          if (selectedMode == "apmode") apModeIconFilled0384
          else apModeIconOutlined0384,
        ),
        emitValue = "apmode",
        isSelected = selectedMode == "apmode",
      ),
    )
  }

  /**
   * Creates a list of SelectButtonItem for error codes.
   *
   * @param errorCodes List of error codes to display
   * @param selectedErrorCode The currently selected error code, if any
   * @return List of SelectButtonItem for error codes
   */
  fun createErrorCodeButtons(
    errorCodes: List<String>,
    selectedErrorCode: String? = null
  ): List<SelectButtonItem> {
    return errorCodes.map { errorCode ->
      SelectButtonItem(
        id = "error_$errorCode",
        displayValue = SelectButtonDisplayValue.ErrorCode(errorCode),
        emitValue = errorCode,
        isSelected = errorCode == selectedErrorCode,
      )
    }
  }

  /**
   * Creates the default error code buttons list as specified in the requirements.
   *
   * @param selectedErrorCode The currently selected error code, if any
   * @return List of SelectButtonItem for the default error codes
   */
  fun createDefaultErrorCodeButtons(
    selectedErrorCode: String? = null
  ): List<SelectButtonItem> {
    val defaultErrorCodes = listOf("t163", "t164", "t165", "t204", "t205", "t206", "t315", "t323", "t325")
    return createErrorCodeButtons(defaultErrorCodes, selectedErrorCode)
  }
}
