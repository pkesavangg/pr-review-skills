package com.greatergoods.meapp.features.common.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private object DragDefaults {
    const val POSITIONAL_THRESHOLD = 0.5f
    const val VELOCITY_THRESHOLD = 100f
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDraggableListItem(
    onActionOpened: (Int) -> Unit,
    isDraggable: Boolean,
    index: Int,
    iconWidth: Dp = 40.dp,
    showAction: Boolean,
    actionContent: @Composable RowScope.() -> Unit,
    content: @Composable (progress: Float) -> Unit,
) {
    val state = rememberDraggableState(
        iconWidth = iconWidth,
    )

    HandleActionState(
        state = state,
        onActionOpened = onActionOpened,
        index = index,
        showAction = showAction,
    )

    Box(
        modifier = Modifier
            .clipToBounds(),
    ) {
        Row(
            modifier = Modifier
                .width(iconWidth)
                .align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actionContent()
        }

        DraggableContentBox(state, isDraggable, iconWidth, content)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun rememberDraggableState(
    iconWidth: Dp,
): AnchoredDraggableState<DragAnchors> {
    val density = LocalDensity.current
    return remember {
        val screenSizePx = with(density) { iconWidth.toPx() }
        AnchoredDraggableState(
            initialValue = DragAnchors.Center,
            anchors = DraggableAnchors {
                DragAnchors.Center at 0f
                DragAnchors.End at screenSizePx
            },
            positionalThreshold = { DragDefaults.POSITIONAL_THRESHOLD * it },
            velocityThreshold = { DragDefaults.VELOCITY_THRESHOLD },
            snapAnimationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
            decayAnimationSpec = exponentialDecay(),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HandleActionState(
    state: AnchoredDraggableState<DragAnchors>,
    onActionOpened: (Int) -> Unit,
    index: Int,
    showAction: Boolean
) {
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == DragAnchors.End) {
            onActionOpened(index)
        }
    }

    LaunchedEffect(showAction) {
        if (!showAction && state.currentValue != DragAnchors.Center) {
            state.animateTo(DragAnchors.Center)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DraggableContentBox(
    state: AnchoredDraggableState<DragAnchors>,
    isDraggable: Boolean,
    iconWidth: Dp = 40.dp,
    content: @Composable (progress: Float) -> Unit
) {
    val density = LocalDensity.current
    val iconWidthPx = with(density) { iconWidth.toPx() }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = -state
                        .requireOffset()
                        .roundToInt(),
                    y = 0,
                )
            }
            .anchoredDraggable(
                state = state,
                orientation = Orientation.Horizontal,
                enabled = isDraggable,
                reverseDirection = true,
                interactionSource = remember { MutableInteractionSource() },
            ),
    ) {
        val progress = (state.requireOffset() / iconWidthPx).coerceIn(0f, 1f)
        content(progress)
    }
}

