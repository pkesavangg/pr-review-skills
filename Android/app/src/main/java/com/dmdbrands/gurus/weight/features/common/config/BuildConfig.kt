package com.dmdbrands.gurus.weight.features.common.config

/**
 * Build configuration constants for the application.
 * These values can be updated to reflect the current build information.
 */
object AppBuildConfig {

  /**
   * Current build number for the application.
   * This should be incremented with each new build.
   */
  const val BUILD_NUMBER = "5.1.0"

  /**
   * Gets the formatted version string based on build type.
   * @param versionName The version name from PackageInfo or BuildConfig
   * @return Formatted version string
   */
  fun getFormattedVersion(versionName: String?): String {
    return "version $BUILD_NUMBER"
  }

}
