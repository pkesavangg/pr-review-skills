package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme

@Composable
fun AppFab(
  offsetX: Dp = 0.dp,
  offsetY: Dp = 0.dp,
  isDraggable: Boolean = true,
  enabled: Boolean = false,
  showWeightOnlyModeAlert: Boolean = false,
  onClick: () -> Unit
) {
  var offsetXState by remember { mutableStateOf(offsetX.value) }
  var offsetYState by remember { mutableStateOf(offsetY.value) }

  FloatingActionButton(
    onClick = onClick,
    containerColor = MeTheme.colorScheme.primaryAction,
    modifier = Modifier
      .offset { IntOffset(offsetXState.toInt(), offsetYState.toInt()) }
      .then(
        if (isDraggable) {
          Modifier.pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
              offsetXState += dragAmount.x
              offsetYState += dragAmount.y
            }
          }
        } else Modifier,
      ),
  ) {
    Icon(
      painter = painterResource(id = AppIcons.Default.WeightOnlyMode),
      contentDescription = if (showWeightOnlyModeAlert) "Enable Body Metrics" else "Weight Only Mode",
      modifier = Modifier.size(24.dp),
      tint = White,
    )
  }
}
