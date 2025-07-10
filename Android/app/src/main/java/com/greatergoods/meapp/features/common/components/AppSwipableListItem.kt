package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import kotlin.math.roundToInt

/**
 * Enum representing the anchors for swipe gestures.
 */
enum class SwipeAnchors {
  Center,
  End
}

/**
 * A single swipeable list item that can reveal one or more actions when swiped.
 *
 * @param onActionOpened Callback when the action is fully revealed (swiped open).
 * @param isSwipeable Whether the item is swipeable.
 * @param index The index of the item in the list.
 * @param iconWidth The width of the action icon area.
 * @param showAction Whether to show the action (i.e., if this item is open).
 * @param actionContent Composable lambda for the trailing action area (can contain multiple actions).
 * @param content Composable lambda for the main item content, receives swipe progress.
 * @param positionalThreshold Fraction of iconWidth to trigger open/close (default: 0.5f).
 * @param velocityThreshold Velocity threshold for swipe (default: 100f).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppSwipeableListItem(
  onActionOpened: (Int?) -> Unit,
  isSwipeable: Boolean,
  index: Int,
  iconWidth: Dp = 40.dp,
  showAction: Boolean,
  actionContent: @Composable RowScope.() -> Unit,
  positionalThreshold: Float = SwipeDefaults.POSITIONAL_THRESHOLD,
  velocityThreshold: Float = SwipeDefaults.VELOCITY_THRESHOLD,
  content: @Composable (progress: Float) -> Unit,
) {
  val state =
    rememberSwipeableState(
      iconWidth = iconWidth,
      positionalThreshold = positionalThreshold,
      velocityThreshold = velocityThreshold,
    )
  
  HandleActionState(
    state = state,
    onActionOpened = onActionOpened,
    index = index,
    showAction = showAction,
  )
  
  Box(
    modifier =
      Modifier
        .clipToBounds(),
  ) {
    Row(
      modifier =
        Modifier
          .width(iconWidth)
          .matchParentSize()
          .pointerInput(
            showAction,
          ) {
            awaitPointerEventScope {
              onActionOpened(null)
            }
          }
          .align(Alignment.CenterEnd),
      horizontalArrangement = Arrangement.End,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      actionContent()
    }
    
    SwipeableContentBox(state, isSwipeable, iconWidth, content)
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun rememberSwipeableState(
  iconWidth: Dp,
  positionalThreshold: Float = SwipeDefaults.POSITIONAL_THRESHOLD,
  velocityThreshold: Float = SwipeDefaults.VELOCITY_THRESHOLD,
): AnchoredDraggableState<SwipeAnchors> {
  val density = LocalDensity.current
  return remember(iconWidth, positionalThreshold, velocityThreshold) {
    val screenSizePx = with(density) { iconWidth.toPx() }
    AnchoredDraggableState(
      initialValue = SwipeAnchors.Center,
      anchors =
        DraggableAnchors {
          SwipeAnchors.Center at 0f
          SwipeAnchors.End at screenSizePx
        },
      // TODO: Need to handle this
      // positionalThreshold = { positionalThreshold * it },
      // velocityThreshold = { velocityThreshold },
      // snapAnimationSpec =
      //     spring(
      //         dampingRatio = Spring.DampingRatioLowBouncy,
      //         stiffness = Spring.StiffnessMedium,
      //     ),
      // decayAnimationSpec = exponentialDecay(),
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HandleActionState(
  state: AnchoredDraggableState<SwipeAnchors>,
  onActionOpened: (Int) -> Unit,
  index: Int,
  showAction: Boolean,
) {
  LaunchedEffect(state.currentValue) {
    if (state.currentValue == SwipeAnchors.End) {
      onActionOpened(index)
    }
  }
  
  LaunchedEffect(showAction) {
    if (!showAction && state.currentValue != SwipeAnchors.Center) {
      state.animateTo(SwipeAnchors.Center)
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeableContentBox(
  state: AnchoredDraggableState<SwipeAnchors>,
  isSwipeable: Boolean,
  iconWidth: Dp = 40.dp,
  content: @Composable (progress: Float) -> Unit,
) {
  val density = LocalDensity.current
  val iconWidthPx = with(density) { iconWidth.toPx() }
  
  Box(
    modifier =
      Modifier
        .offset {
          IntOffset(
            x =
              -state
                .requireOffset()
                .roundToInt(),
            y = 0,
          )
        }
        .anchoredDraggable(
          state = state,
          orientation = Orientation.Horizontal,
          enabled = isSwipeable,
          reverseDirection = true,
          interactionSource = remember { MutableInteractionSource() },
        ),
  ) {
    val progress = (state.requireOffset() / iconWidthPx).coerceIn(0f, 1f)
    content(progress)
  }
}

// region: Previews

@PreviewTheme
@Composable
private fun PreviewAppSwipeableListItem() {
  MeAppTheme {
    AppSwipeableListItem(
      onActionOpened = {},
      isSwipeable = true,
      index = 0,
      showAction = false,
      actionContent = {
        AppSwipeableListActions {
          AppSwipeableActionItem(
            iconId = AppIcons.Default.Delete,
            contentDescription = "Delete",
            backgroundColor = MeTheme.colorScheme.danger,
            onClick = {},
          )
        }
      },
      content = { progress ->
        Text("Item 1 (progress: $progress)")
      },
    )
  }
}

// endregion
