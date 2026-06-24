package com.dmdbrands.gurus.weight.theme

import com.dmdbrands.gurus.weight.proto.ThemeMode
import android.content.res.Configuration

/**
 * Pure helpers for translating a [ThemeMode] into a Configuration.uiMode night flag.
 *
 * Extracted from MainActivity.attachBaseContext and MeAppTheme so the (easily mis-written)
 * bit-masking lives in one unit-tested place — a regression here is exactly what reintroduces
 * MA-3996 (theme-aware drawables not following the in-app Appearance pick).
 */

/**
 * Resolves the `UI_MODE_NIGHT_*` flag for a [ThemeMode].
 *
 * LIGHT/DARK short-circuit to their explicit flag. SYSTEM/UNRECOGNIZED defer to whatever
 * night bits the supplied [currentUiMode] already carries (i.e. follow the OS).
 */
fun resolveNightFlag(mode: ThemeMode, currentUiMode: Int): Int = when (mode) {
  ThemeMode.LIGHT -> Configuration.UI_MODE_NIGHT_NO
  ThemeMode.DARK -> Configuration.UI_MODE_NIGHT_YES
  else -> currentUiMode and Configuration.UI_MODE_NIGHT_MASK
}

/**
 * Returns [uiMode] with its night bits replaced by [nightFlag], preserving all other
 * dimensions (device-type bits such as `UI_MODE_TYPE_*`).
 */
fun applyNightFlag(uiMode: Int, nightFlag: Int): Int =
  (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightFlag
