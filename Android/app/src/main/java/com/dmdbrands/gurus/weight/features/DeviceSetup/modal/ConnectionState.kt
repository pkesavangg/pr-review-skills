package com.dmdbrands.gurus.weight.features.DeviceSetup.modal

/**
 * Represents the connection state for the setup loader.
 */
sealed class ConnectionState {
  /** Loading state with animated dots */
  data object Loading : ConnectionState()

  /** Success state with check icon */
  data object Success : ConnectionState()

  sealed class Failed : ConnectionState() {

    data object Error : Failed()

    data class ErrorWithMessage(val message: String) : Failed()
  }
}

