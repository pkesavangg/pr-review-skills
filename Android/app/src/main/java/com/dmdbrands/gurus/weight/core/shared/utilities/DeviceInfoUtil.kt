package com.dmdbrands.gurus.weight.core.shared.utilities

import com.dmdbrands.gurus.weight.BuildConfig
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings

/**
 * Utility object for retrieving device and app information.
 */
object DeviceInfoUtil {
    /**
     * Returns the app version name.
     */
    fun getAppVersion(): String = BuildConfig.VERSION_NAME

    /**
     * Returns the device manufacturer.
     */
    fun getManufacturer(): String = Build.MANUFACTURER ?: ""

    /**
     * Returns the device model.
     */
    fun getModel(): String = Build.MODEL ?: ""

    /**
     * Returns the device OS name.
     */
    fun getOSName(): String = "Android"

    /**
     * Returns the device OS version.
     */
    fun getOSVersion(): String = Build.VERSION.RELEASE ?: ""

    /**
     * Returns the device UUID (ANDROID_ID).
     * @param context The application context.
     */
    fun getDeviceUUID(context: Context): String =
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID,
        ) ?: ""

    /**
     * Check if WiFi is enabled on the device.
     * Equivalent to device.service.ts isWifiEnabled()
     *
     * @param context The application context.
     * @return true if WiFi is enabled, false otherwise
     */
    fun isWifiEnabled(context: Context): Boolean {
        return try {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiManager.isWifiEnabled
        } catch (e: Exception) {
            false
        }
    }
}
