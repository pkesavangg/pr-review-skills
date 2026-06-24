package com.dmdbrands.gurus.weight.core.navigation

import androidx.compose.runtime.compositionLocalOf
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService

/**
 * CompositionLocal for providing the dialog queue service throughout the Compose hierarchy.
 * Provided at app level in MeApp.
 */
val LocalDialogQueueService =
  compositionLocalOf<IDialogQueueService> {
    error("No DialogQueueService provided")
  }
