package com.greatergoods.ggInAppMessaging.util

import androidx.browser.customtabs.CustomTabsIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri

/**
 * Utility for opening links in various ways
 * Provides methods to open URLs in Chrome, custom tabs, or fallback browsers
 */
object LinkOpener {

  private const val CHROME_PACKAGE = "com.android.chrome"

  /**
   * Prefer Chrome app. If not installed, fall back to any browser.
   */
  fun openInChromeOrDefault(context: Context, url: String) {
    val uri = Uri.parse(url)
    val chrome = Intent(Intent.ACTION_VIEW, uri).apply {
      setPackage(CHROME_PACKAGE)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
      context.startActivity(chrome)
    } catch (_: ActivityNotFoundException) {
      // Fallback: any capable browser
      context.startActivity(
        Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
      )
    }
  }

  /**
   * Open with Chrome Custom Tabs if available; falls back to normal VIEW intent.
   * Keeps user in your app with a browser chrome (toolbar).
   */
  fun openInCustomTab(
    context: Context,
    url: String?,
    toolbarColor: Int = Color.BLACK, // you can expose theme color
    showTitle: Boolean = true
  ) {
    if (url == null || url.isEmpty()) {
      return
    }
    val uri = Uri.parse(url)
    try {
      val intent = CustomTabsIntent.Builder()
        .setShowTitle(showTitle)
        .setToolbarColor(toolbarColor)
        .build()

      // Prefer Chrome for custom tabs if present; not strictly required
      intent.intent.`package` = CHROME_PACKAGE

      intent.launchUrl(context, uri)
    } catch (_: Exception) {
      // Fallback to any browser
      context.startActivity(
        Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
      )
    }
  }
}
