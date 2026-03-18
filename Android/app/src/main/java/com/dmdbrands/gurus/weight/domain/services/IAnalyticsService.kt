package com.dmdbrands.gurus.weight.domain.services

import android.os.Bundle

interface IAnalyticsService {
    fun logEvent(eventName: String, params: Bundle? = null)

    object Events {
        const val WEIGHT_ENTRY_CREATED = "weight_entry_created"
        const val MANUAL_ENTRY_CREATED = "manual_entry_created"
        const val SCALE_CONNECTED = "scale_connected"
        const val ACCOUNT_SWITCHED = "account_switched"
        const val LOGIN_SUCCESS = "login_success"
        const val LOGIN_FAILURE = "login_failure"
        const val SIGNUP_COMPLETED = "signup_completed"
    }

    object Params {
        const val ENTRY_SOURCE = "entry_source"
        const val SCALE_TYPE = "scale_type"
        const val ERROR_TYPE = "error_type"
    }
}
