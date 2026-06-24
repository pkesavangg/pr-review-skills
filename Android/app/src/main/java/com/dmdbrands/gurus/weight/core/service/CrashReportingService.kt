package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.BuildConfig
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.services.ICrashReportingService
import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrashReportingService
    @Inject
    constructor() : ICrashReportingService {
        companion object {
            private const val TAG = "CrashReportingService"
        }

        private val crashlytics: FirebaseCrashlytics by lazy {
            FirebaseCrashlytics.getInstance()
        }

        override fun initialize() {
            crashlytics.isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG
            AppLog.d(TAG, "Crashlytics initialized (collection enabled: ${!BuildConfig.DEBUG})")
        }

        override fun recordException(throwable: Throwable, tag: String?) {
            if (tag != null) {
                crashlytics.setCustomKey("error_tag", tag)
            }
            crashlytics.recordException(throwable)
        }

        override fun setCustomKey(key: String, value: String) {
            crashlytics.setCustomKey(key, value)
        }

        override fun log(message: String) {
            crashlytics.log(message)
        }

        override fun setCollectionEnabled(enabled: Boolean) {
            crashlytics.isCrashlyticsCollectionEnabled = enabled
        }
    }
