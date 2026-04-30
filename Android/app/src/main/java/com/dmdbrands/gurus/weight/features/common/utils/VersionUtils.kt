package com.dmdbrands.gurus.weight.features.common.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.dmdbrands.gurus.weight.BuildConfig
import com.dmdbrands.gurus.weight.features.common.config.AppBuildConfig
import android.content.Context

/**
 * Utility object for version and build information display.
 */
object VersionUtils {

  /**
   * Gets version text based on build type using centralized build configuration.
   * @param context Android context for accessing PackageManager
   * @return Version text for production builds, build number for debug/release builds
   */
  fun getVersionText(context: Context): String {
    return try {
      val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
      AppBuildConfig.getFormattedVersion(packageInfo.versionName)
    } catch (e: Exception) {
      // Fallback to BuildConfig values if PackageManager fails
      AppBuildConfig.getFormattedVersion(BuildConfig.VERSION_NAME)
    }
  }
}

/**
 * Composable helper to get version text.
 * @return Version text for production builds, build number for debug/release builds
 */
@Composable
fun rememberVersionText(): String {
  val context = LocalContext.current
  return VersionUtils.getVersionText(context)
}
