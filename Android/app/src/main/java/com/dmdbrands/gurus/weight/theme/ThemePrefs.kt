package com.dmdbrands.gurus.weight.theme

import com.dmdbrands.gurus.weight.proto.ThemeMode
import android.content.Context

/**
 * Fast, synchronous SharedPreferences cache of the last applied [ThemeMode].
 *
 * `attachBaseContext` runs before Hilt injection and before the first frame, so it cannot use the
 * injected repositories and must not block on a proto DataStore disk read there (ANR/jank risk on
 * the cold-start path — see MA-3996). The canonical source of truth remains the DataStore-backed
 * AppRepository; this cache is written whenever a theme is applied so `attachBaseContext` can read
 * it cheaply without touching DataStore.
 */
object ThemePrefs {
  private const val PREFS_NAME = "theme_prefs"
  private const val KEY_THEME_MODE = "theme_mode"

  /** Persists the applied [mode] so the next `attachBaseContext` can read it synchronously. */
  fun save(context: Context, mode: ThemeMode) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putInt(KEY_THEME_MODE, mode.number)
      .apply()
  }

  /** Reads the cached [ThemeMode], defaulting to [ThemeMode.SYSTEM] when nothing is stored yet. */
  fun read(context: Context): ThemeMode {
    val number = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .getInt(KEY_THEME_MODE, ThemeMode.SYSTEM.number)
    return ThemeMode.forNumber(number) ?: ThemeMode.SYSTEM
  }
}
