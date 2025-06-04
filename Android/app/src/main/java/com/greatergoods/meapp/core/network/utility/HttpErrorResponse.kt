package com.greatergoods.meapp.core.network.utility

import com.greatergoods.meapp.core.config.HttpErrorConfig.ResponseCode
import com.greatergoods.meapp.core.config.HttpErrorConfig.ResponseMessage

object HttpErrorResponse {
    /**
     * Get error message based on status code
     */
    fun getErrorMessage(statusCode: Int): String {
        return when (statusCode) {
            ResponseCode.NO_INTERNET_CONNECTION -> ResponseMessage.NO_CONNECTION
            ResponseCode.UNAUTHORIZED -> ResponseMessage.NOT_AUTHORIZED
            ResponseCode.INTERNAL_SERVER_ERROR -> ResponseMessage.SERVER_ERROR

            else -> "Unknown error occurred"
        }
    }
    /**
     * Check if the status code indicates an authentication error
     */
    fun isAuthError(statusCode: Int): Boolean {
        return statusCode == ResponseCode.UNAUTHORIZED || statusCode == ResponseCode.FORBIDDEN
    }

    /**
     * Check if the status code indicates a server error
     */
    fun isServerError(statusCode: Int): Boolean {
        return statusCode >= 500
    }

    /**
     * Check if the status code indicates a client error
     */
    fun isClientError(statusCode: Int): Boolean {
        return statusCode in 400..499
    }

    /**
     * Check if the status code indicates a network error
     */
    fun isNetworkError(statusCode: Int): Boolean {
        return statusCode == ResponseCode.NO_INTERNET_CONNECTION
    }
}
