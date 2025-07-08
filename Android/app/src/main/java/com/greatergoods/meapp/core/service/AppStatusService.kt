package com.greatergoods.meapp.core.service

import com.greatergoods.meapp.BuildConfig
import com.greatergoods.meapp.core.config.AppConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Service for app status and environment information.
 * Based on Angular's AppStatus service.
 */
object AppStatusService {
  val isDev: Boolean = BuildConfig.DEBUG

  val version: String = BuildConfig.VERSION_NAME

  val apiUrl: String = AppConfig.BASE_URL

  val isNative: Boolean = true // Android is always native

  val isAndroid: Boolean = true

  val isMetric: Boolean = false // TODO: Get from user preferences

  val enableTestingFeatures: Boolean = BuildConfig.DEBUG

  val showDownloadLogOption: Boolean = BuildConfig.DEBUG

  val canShowRateAppItem: Boolean = true

  fun getCurrentDateTime(): String {
    val formatter = SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())
    return formatter.format(Date())
  }

  fun getUserTimezone(): String = TimeZone.getDefault().displayName

  fun getUserTimezoneOffset(): String {
    val offsetMillis = TimeZone.getDefault().rawOffset
    val offsetMinutes = offsetMillis / (1000 * 60)
    return if (offsetMinutes >= 0) {
      "+$offsetMinutes"
    } else {
      "$offsetMinutes"
    }
  }
}
