package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.compose.ui.zIndex
import com.dmdbrands.gurus.weight.features.common.components.strings.AppFabStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme
import kotlinx.coroutines.launch

@Composable
fun AppFab(
    offsetX: Dp = 0.dp,
    offsetY: Dp = 0.dp,
    showWeightOnlyModeAlert: Boolean = false,
    onClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }
    var isDraggingOn by remember { mutableStateOf(false) }

    // Use a single Animatable for the Offset to ensure path is always a straight line.
    val position =
        remember { Animatable(Offset(offsetX.value, offsetY.value), Offset.VectorConverter) }

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    // Get screen dimensions
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    // Get system bar and keyboard (IME) insets
    val systemBarsPadding = WindowInsets.systemBars.asPaddingValues()
    val imePadding = WindowInsets.ime.asPaddingValues()

    val topPadding = systemBarsPadding.calculateTopPadding()
    val bottomPadding =
        maxOf(systemBarsPadding.calculateBottomPadding(), imePadding.calculateBottomPadding())

    // Assume a standard TopAppBar height to prevent dragging over it.
    val topAppBarHeight = 64.dp

    // The actual content area for the screen, minus the app bar.
    val contentHeight = screenHeight - topPadding - bottomPadding - topAppBarHeight

    // Size and padding of the FAB - matched to design spec
    val fabSize = 64.dp // Increased size to match design
    val fabPadding = 16.dp

    // --- Corner Definitions ---
    val rightBoundary = 0f
    val leftBoundary = with(density) { -(screenWidth - fabSize - (2 * fabPadding)).toPx() }
    val bottomBoundary = 0f
    val topBoundary = with(density) { -(contentHeight - fabSize - (2 * fabPadding)).toPx() }

    val corners = listOf(
        Offset(leftBoundary, topBoundary),    // Top-left
        Offset(rightBoundary, topBoundary),   // Top-right
        Offset(leftBoundary, bottomBoundary), // Bottom-left
        Offset(rightBoundary, bottomBoundary), // Bottom-right
    )

    // This effect runs whenever the boundaries change (e.g., keyboard appears).
    LaunchedEffect(leftBoundary, topBoundary, rightBoundary, bottomBoundary) {
        position.updateBounds(
            lowerBound = Offset(leftBoundary, topBoundary),
            upperBound = Offset(rightBoundary, bottomBoundary),
        )
    }

    val elevation: Dp = if (isPressed || isDraggingOn) 8.dp else 0.dp
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> isPressed = true
                is PressInteraction.Release -> isPressed = false
                is PressInteraction.Cancel -> isPressed = false
            }
        }
    }
    FloatingActionButton(
        onClick =
            onClick,
        interactionSource = interactionSource,
        shape = CircleShape,
        containerColor = if (isPressed || isDraggingOn) MeTheme.colorScheme.primaryAction else MeTheme.colorScheme.primaryAction.copy(
            alpha = 0.7f
        ),
        elevation = FloatingActionButtonDefaults.elevation(elevation),
        modifier = Modifier
            .size(fabSize) // Explicit size to match design
            .offset { IntOffset(position.value.x.toInt(), position.value.y.toInt()) }
            .clip(CircleShape)
            .zIndex(1f)
            .pointerInput(leftBoundary, topBoundary, rightBoundary, bottomBoundary) {
                detectDragGestures(
                    onDragStart = { isDraggingOn = true },
                    onDrag = { change, dragAmount ->
                        isDraggingOn = true
                        scope.launch {
                            val newPosition = position.value + dragAmount
                            val coercedPosition = Offset(
                                x = newPosition.x.coerceIn(leftBoundary, rightBoundary),
                                y = newPosition.y.coerceIn(topBoundary, bottomBoundary),
                            )
                            position.snapTo(coercedPosition)
                        }
                    },
                    onDragCancel = { isDraggingOn = false },
                    onDragEnd = {
                        isDraggingOn = false
                        scope.launch {
                            val nearestCorner = corners.minByOrNull { corner ->
                                (corner - position.value).getDistance()
                            }
                            if (nearestCorner != null) {
                                position.animateTo(nearestCorner)
                            }
                        }
                    },
                )
            },
    ) {
        Icon(
            painter = painterResource(id = AppIcons.Default.WeightOnlyMode),
            contentDescription = if (showWeightOnlyModeAlert) AppFabStrings.accEnableBodyMetricsLabel else AppFabStrings.accWeightOnlyModeLabel,
            modifier = Modifier
                .size(32.dp)
                .background(color = Color.Transparent),
            tint = MeTheme.colorScheme.inverseAction,
            // Larger icon size to match design
        )
    }
}

