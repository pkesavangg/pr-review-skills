package com.dmdbrands.gurus.weight.core.power.utility

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.dmdbrands.gurus.weight.core.power.interfaces.IPowerSaveModeObserver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/**
 * Observes Power Saving Mode (Battery Saver) via [PowerManager] and the system
 * [PowerManager.ACTION_POWER_SAVE_MODE_CHANGED] broadcast. Implements [IPowerSaveModeObserver].
 *
 * @constructor Injects the application context for system service / broadcast registration.
 */
class PowerSaveModeObserver
  @Inject
  constructor(
    @ApplicationContext private val context: Context,
  ) : IPowerSaveModeObserver {

    private val powerManager = context.getSystemService<PowerManager>()

    override fun isPowerSaveMode(): Boolean = powerManager?.isPowerSaveMode == true

    override fun observe(): Flow<Boolean> =
      callbackFlow {
        // Emit the current state immediately so collectors don't wait for the first toggle.
        trySend(isPowerSaveMode())

        val receiver =
          object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
              trySend(isPowerSaveMode())
            }
          }
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        // RECEIVER_NOT_EXPORTED: this is a protected system broadcast, so it is still delivered
        // while keeping the receiver private to the app (required on Android 13+).
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        awaitClose { context.unregisterReceiver(receiver) }
      }.distinctUntilChanged()
  }
