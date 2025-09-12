package com.dmdbrands.gurus.weight.core.shared.utilities

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.enums.AuthAction
import com.dmdbrands.gurus.weight.domain.services.AuthState
import retrofit2.HttpException

/**
 * Generic error handler for suspend functions that logs, optionally shows toast, and emits event.
 *
 * @param tag The log tag for AppLog
 * @param action The AuthAction for context (optional)
 * @param showErrorToast Optional lambda to show error toast (AuthAction, HttpException?)
 * @param emitEvent Optional lambda to emit an event (AuthState or any type)
 * @param onError Optional lambda for additional error handling
 * @param block The suspend function to execute
 * @return The result of block, or null if an exception occurred
 */
suspend fun <T> runCatchingWithHandlers(
    tag: String,
    action: AuthAction? = null,
    showErrorToast: ((AuthAction, HttpException?) -> Unit)? = null,
    emitEvent: ((Any) -> Unit)? = null,
    onError: ((Exception) -> Unit)? = null,
    block: suspend () -> T,
): T? =
    try {
        block()
    } catch (e: Exception) {
        AppLog.e(tag, "${action?.name ?: "Operation"} failed", e)
        if (action != null && showErrorToast != null) {
            showErrorToast(action, e as? HttpException)
        }
        if (emitEvent != null) {
            emitEvent(AuthState.Error(e.message ?: "${action?.name ?: "Operation"} failed"))
        }
        onError?.invoke(e)
        null
    }
