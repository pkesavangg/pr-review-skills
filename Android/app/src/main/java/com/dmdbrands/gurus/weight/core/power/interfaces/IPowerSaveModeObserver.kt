package com.dmdbrands.gurus.weight.core.power.interfaces

import kotlinx.coroutines.flow.Flow

/**
 * Observes the device's Power Saving Mode (Battery Saver) state.
 *
 * When Power Saving Mode is enabled the OS throttles the CPU and reduces frame dispatch, which
 * makes the app's continuous/looping animations stutter and steals cycles from touch/scroll
 * handling. Components use this signal to drop those animations to a static frame while Power
 * Saving Mode is on, keeping the app responsive (MOB-226).
 */
interface IPowerSaveModeObserver {
  /**
   * Observes Power Saving Mode changes as a Flow. Emits the current value immediately on
   * collection and again whenever the OS toggles Battery Saver.
   * @return Flow emitting true when Power Saving Mode is enabled, false otherwise.
   */
  fun observe(): Flow<Boolean>

  /**
   * Reads the current Power Saving Mode state synchronously.
   * @return true when Power Saving Mode is enabled, false otherwise.
   */
  fun isPowerSaveMode(): Boolean
}
