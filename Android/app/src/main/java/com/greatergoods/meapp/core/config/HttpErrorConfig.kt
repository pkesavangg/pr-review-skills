package com.greatergoods.meapp.core.config

import com.greatergoods.meapp.core.config.HttpErrorConfig.ResponseCode.FORBIDDEN
import com.greatergoods.meapp.core.config.HttpErrorConfig.ResponseCode.INTERNAL_SERVER_ERROR
import com.greatergoods.meapp.core.config.HttpErrorConfig.ResponseCode.UNAUTHORIZED

class HttpErrorConfig {
    /**
     * Configuration class for HTTP error responses and status codes
     */
    object ResponseCode {
        // HTTP Status Codes
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
        const val INTERNAL_SERVER_ERROR = 500
        const val BAD_REQUEST = 400
        const val NO_INTERNET_CONNECTION = 599
    }
    object ResponseMessage {
        // HTTP Error Messages
        const val NO_CONNECTION = "No connection found"
        const val NOT_AUTHORIZED = "Unauthorized"
        const val SERVER_ERROR = "Server Error"
    }


}
