package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.debugInspectorInfo

fun Modifier.debounceClick(
  debounceTime: Long = 500L,
  onClick: () -> Unit
): Modifier = composed(
  inspectorInfo = debugInspectorInfo {
    name = "debounceClick"
    value = debounceTime
  },
) {
  var lastEmitTime by rememberSaveable { mutableStateOf(0L) }
  val onClickState = rememberUpdatedState(onClick)
  this.clickable {
    // Use monotonic clock to avoid time changes affecting logic
    val currentTime = android.os.SystemClock.elapsedRealtime()
    if (currentTime - lastEmitTime >= debounceTime) {
      lastEmitTime = currentTime
      onClickState.value()
    }
  }
}
