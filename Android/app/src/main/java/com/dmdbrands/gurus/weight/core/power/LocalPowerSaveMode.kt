package com.dmdbrands.gurus.weight.core.power

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * CompositionLocal exposing the device's Power Saving Mode (Battery Saver) state to the whole
 * Compose tree. Provided at app level in MeApp from [com.dmdbrands.gurus.weight.core.power.interfaces.IPowerSaveModeObserver].
 *
 * Components read this to drop continuous/looping animations to a static frame while Power Saving
 * Mode is on, so the OS CPU throttling does not make the app stutter (MOB-226).
 *
 * Uses [staticCompositionLocalOf] because the value changes rarely; when it does flip, recomposing
 * the affected subtree is exactly the desired behaviour. Defaults to false when no provider is set.
 */
val LocalPowerSaveMode = staticCompositionLocalOf { false }

/**
 * Convenience accessor for [LocalPowerSaveMode]. Returns true when Power Saving Mode is enabled.
 */
@Composable
@ReadOnlyComposable
fun isPowerSaveMode(): Boolean = LocalPowerSaveMode.current
