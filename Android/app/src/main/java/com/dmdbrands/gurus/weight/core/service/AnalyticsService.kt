package com.dmdbrands.gurus.weight.core.service

import android.os.Bundle
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.services.IAnalyticsService
import com.google.firebase.analytics.FirebaseAnalytics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsService
    @Inject
    constructor(
        private val firebaseAnalytics: FirebaseAnalytics,
    ) : IAnalyticsService {
        companion object {
            private const val TAG = "AnalyticsService"
        }

        override fun logEvent(eventName: String, params: Bundle?) {
            firebaseAnalytics.logEvent(eventName, params)
            AppLog.d(TAG, "Event logged: $eventName")
        }
    }
