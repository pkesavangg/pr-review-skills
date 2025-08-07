package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.strings.ToastStrings

/**
 * Base class for all services, providing common utilities such as network checks and generic toast construction.
 */
abstract class BaseService(
  protected val connectivityObserver: IConnectivityObserver,
  protected val dialogQueueService: IDialogQueueService,
  protected val appNavigationService: IAppNavigationService
) {
  /**
   * Checks if network is available using the connectivity observer.
   */
  protected fun isNetworkAvailable(): Boolean = !connectivityObserver.getCurrentNetworkState().unAvailable

  /**
   * Throws an exception and runs [onError] if network is not available, otherwise runs [block].
   */
  protected inline fun requireNetworkAvailable(
    onError: () -> Unit,
    block: () -> Unit = {},
  ) {
    if (!isNetworkAvailable()) {
      onError()
    } else {
      block()
    }
  }

  /**
   * Shows a network error toast and throws an exception for network-dependent operations.
   * @throws Exception with network error message
   */
  protected fun showNetworkErrorAndThrow() {
    dialogQueueService.showToast(
      Toast(
        title = null,
        message = ToastStrings.Error.NetworkError.Message,
        action = null,
      ),
    )
    throw Exception("No network connection available")
  }

  /**
   * Shows a generic success toast with the given title and message.
   */
  protected fun showSuccessToast(
    title: String,
    message: String,
  ) {
    dialogQueueService.showToast(Toast(title = title, message = message, action = null))
  }

  /**
   * Shows a generic error toast with the given title and message.
   */
  protected fun showErrorToast(
    title: String,
    message: String,
  ) {
    dialogQueueService.showToast(Toast(title = title, message = message, action = null))
  }
}
