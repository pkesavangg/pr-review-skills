package com.greatergoods.meapp.features.ScaleSetup.modal

/**
 * Represents the connection state for the setup loader.
 */
sealed class ConnectionState {
  /** Loading state with animated dots */
  data object Loading : ConnectionState()

  /** Success state with check icon */
  data object Success : ConnectionState()

  /** Error state with error icon */
  data object Error : ConnectionState()

  data class ErrorWithMessage(val message: String) : ConnectionState()
}
