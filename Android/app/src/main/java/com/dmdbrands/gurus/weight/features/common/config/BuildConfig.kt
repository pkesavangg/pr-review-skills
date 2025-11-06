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
  const val BUILD_NUMBER = "1.0.0"

  /**
   * Gets the formatted version string based on build type.
   * @param versionName The version name from PackageInfo or BuildConfig
   * @return Formatted version string
   */
  fun getFormattedVersion(versionName: String?): String {
    return "version $BUILD_NUMBER"
    
  }

  /**
   * Determines if the current build is a production build.
   * Automatically detects based on build configuration.
   * @return true if this is a production build, false otherwise
   */
  private fun isProductionBuild(): Boolean {
    return try {
      // Check if this is a release build (not debug)
      com.dmdbrands.gurus.weight.BuildConfig.BUILD_TYPE
      val isDebug = com.dmdbrands.gurus.weight.BuildConfig.DEBUG

      // Production = release build type AND not debug
      !isDebug
    } catch (e: Exception) {
      // Fallback: if we can't detect, assume development
      false
    }
  }
}
