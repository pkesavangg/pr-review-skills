package com.dmdbrands.gurus.weight.features.integration.viewmodel

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.services.IIntegrationService
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.integration.strings.IntegrationStrings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Tag for [submitIntegrationSuggestion] log lines. */
private const val TAG = "IntegrationRequestHandler"

/**
 * Opens the "Request an Integration" modal. On confirm with non-blank input,
 * submits the suggestion via [service] and toasts the outcome. Kept outside
 * [IntegrationViewModel] to keep the class size under the detekt LargeClass
 * threshold without losing testability.
 */
internal fun enqueueIntegrationRequestModal(
  scope: CoroutineScope,
  dialogQueueService: IDialogQueueService,
  service: IIntegrationService,
) = dialogQueueService.enqueue(
  DialogModel.Custom(
    contentKey = DialogType.RequestIntegration,
    dismissOnBackPress = true,
    dismissOnClickOutside = true,
    onConfirm = { input ->
      val requested = (input as? String)?.trim().orEmpty()
      if (requested.isNotEmpty()) {
        scope.launch { submitIntegrationSuggestion(requested, dialogQueueService, service) }
      }
    },
  ),
)

private suspend fun submitIntegrationSuggestion(
  suggestion: String,
  dialogQueueService: IDialogQueueService,
  service: IIntegrationService,
) {
  dialogQueueService.showLoader(IntegrationStrings.loading)
  val message = try {
    service.submitIntegrationRequest(suggestion)
    IntegrationStrings.RequestSubmittedToast
  } catch (e: CancellationException) {
    // The screen was left mid-request; let cancellation propagate so structured
    // concurrency is preserved and no false failure toast is shown.
    throw e
  } catch (e: Exception) {
    AppLog.e(TAG, "Failed to submit integration request", e)
    IntegrationStrings.RequestFailedToast
  }
  dialogQueueService.dismissLoader()
  dialogQueueService.showToast(Toast.Simple(message))
}
