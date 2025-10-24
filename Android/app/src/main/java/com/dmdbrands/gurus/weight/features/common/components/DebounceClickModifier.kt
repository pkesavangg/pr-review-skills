package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.debugInspectorInfo

fun Modifier.debounceClick(
  debounceTime: Long = 700L,
  enabled: Boolean = true,
  onClick: () -> Unit
  ): Modifier = composed(
  inspectorInfo = debugInspectorInfo {
    name = "debounceClick"
    value = debounceTime
  },
  ) {
  var lastEmitTime by rememberSaveable { mutableStateOf(0L) }
  val onClickState = rememberUpdatedState(onClick)
  val interactionSource = remember { MutableInteractionSource() }
  this.clickable(
    enabled = enabled,
    indication = null,
    interactionSource = interactionSource
  ) {
    // Use monotonic clock to avoid time changes affecting logic
    val currentTime = android.os.SystemClock.elapsedRealtime()
    if (currentTime - lastEmitTime >= debounceTime) {
      lastEmitTime = currentTime
      onClickState.value()
    }
  }
}
