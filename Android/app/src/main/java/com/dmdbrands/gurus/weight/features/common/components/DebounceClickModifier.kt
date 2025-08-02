package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun Modifier.debounceClick(
  debounceInterval: Long = 500L,
  onClick: () -> Unit
): Modifier {
  var lastClickTime by remember { mutableStateOf(0L) }

  return pointerInput(Unit) {
    awaitEachGesture {
      val event = awaitFirstDown()
      val currentTime = System.currentTimeMillis()
      if (currentTime - lastClickTime >= debounceInterval) {
        lastClickTime = currentTime
        onClick()
      }
      event.consume()
    }
  }
}
