package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.proto.ThemeMode
import com.greatergoods.meapp.theme.MeAppTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Represents the different sizes available for segment buttons.
 */
enum class SegmentButtonSize {
    /** Small segment button (height: 32dp) */
    Small,

    /** Medium segment button (height: 40dp) */
    Medium,

    /** Large segment button (height: 48dp) */
    Large
}

data class SegmentOption(
    val id: Int,
    val label: String,
)

/**
 * Default values and styling for segment buttons, following the design system.
 */
object SegmentButtonDefaults {
    /**
     * Returns the height for the given segment button size.
     */
    fun height(size: SegmentButtonSize): Dp = when (size) {
        SegmentButtonSize.Small -> 38.dp
        SegmentButtonSize.Medium -> 40.dp
        SegmentButtonSize.Large -> 48.dp
    }

    /**
     * Returns the horizontal padding for the given segment button size.
     */
    @Composable
    fun horizontalPadding(size: SegmentButtonSize): Dp = when (size) {
        SegmentButtonSize.Small -> MeAppTheme.spacing.sm
        SegmentButtonSize.Medium -> MeAppTheme.spacing.md
        SegmentButtonSize.Large -> MeAppTheme.spacing.lg
    }

    /**
     * Returns the text style for the given segment button size.
     */
    @Composable
    fun textStyle(size: SegmentButtonSize): TextStyle = when (size) {
        SegmentButtonSize.Small -> MeAppTheme.typography.button2
        SegmentButtonSize.Medium -> MeAppTheme.typography.button1
        SegmentButtonSize.Large -> MeAppTheme.typography.button1
    }

    /**
     * Returns the corner radius for the segment button container.
     */
    fun cornerRadius(): Dp = 8.dp

    @Composable
    fun colors(): SegmentedButtonColors = SegmentedButtonDefaults.colors(
        activeContainerColor = MeAppTheme.colorScheme.secondaryAction,
        inactiveContainerColor = Color.Transparent,
        activeBorderColor = Color.Transparent,
        inactiveBorderColor = Color.Transparent,
        activeContentColor = MeAppTheme.colorScheme.inverse,
        inactiveContentColor = MeAppTheme.colorScheme.heading,
    )
}

/**
 * A segmented button component that displays multiple options in a horizontal row,
 * allowing single selection similar to radio buttons.
 *
 * @param segments List of segment labels to display
 * @param selectedIndex Index of the currently selected segment
 * @param onSegmentSelected Callback when a segment is selected
 * @param modifier Modifier to be applied to the component
 * @param size Size variant of the segment button
 * @param enabled Whether the component is enabled for interaction
 */
@Composable
fun SegmentButton(
    segments: List<SegmentOption>,
    selectedIndex: Int,
    onSegmentSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    size: SegmentButtonSize = SegmentButtonSize.Large,
) {

    val height = SegmentButtonDefaults.height(size)
    val horizontalPadding = SegmentButtonDefaults.horizontalPadding(size)
    val colors = SegmentButtonDefaults.colors()
    val textStyle = SegmentButtonDefaults.textStyle(size)
    val shape = RoundedCornerShape(SegmentButtonDefaults.cornerRadius())
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val itemWidthDp = 150.dp
    var calculatedItemWidthPx by remember { mutableIntStateOf(150) }
    var rowWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val centerOffsetPx = with(density) {
        // Calculate center offset based on the dynamically calculated item width
        (rowWidthPx - calculatedItemWidthPx) / 2f
    }

    LaunchedEffect(selectedIndex, rowWidthPx, calculatedItemWidthPx) { // Add calculatedItemWidthPx to dependencies
        if (segments.isNotEmpty() && calculatedItemWidthPx > 0) { // Ensure width is calculated
            coroutineScope.launch {
                listState.animateScrollToItem(
                    index = selectedIndex,
                    scrollOffset = -centerOffsetPx.roundToInt()
                )
            }
        }
    }
    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .height(itemWidthDp)
            .onSizeChanged { rowWidthPx = it.width }
    ) {
        itemsIndexed(segments) { index, option ->
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .height(height) // Ensures children take max height
                    .onSizeChanged {
                        // This calculates the width of the current segment button.
                        // You might want to take the max width of all items if they vary.
                        // For simplicity, we'll just set it for the first one encountered or update if a larger one is found.
                        if (it.width > calculatedItemWidthPx) {
                            calculatedItemWidthPx = it.width
                        }
                    }
            ) {
                SegmentedButton(
                    shape = shape,
                    onClick = { onSegmentSelected(index) },
                    colors = colors,
                    icon = {},
                    selected = index == selectedIndex,
                    label = { Text(option.label, style = textStyle) },
                )
            }
        }
    }
}


/**
 * Preview for dark theme with different segment button configurations.
 */
@PreviewTheme
@Composable
fun SegmentButtonPreviewDark() {
    MeAppTheme(themeMode = ThemeMode.DARK) {
        Column(
            modifier = Modifier
                .background(MeAppTheme.colorScheme.primary)
                .padding(MeAppTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MeAppTheme.spacing.md),
        ) {
            // Small size
            SegmentButton(
                segments = listOf("Day", "Week", "Month").mapIndexed { index, label ->
                    SegmentOption(
                        id = index,
                        label = label,
                    )
                },
                selectedIndex = 1,
                onSegmentSelected = { },
                size = SegmentButtonSize.Small,
            )

            // Medium size (default)
            SegmentButton(
                segments = listOf(
                    "Overview",
                    "Details",
                    "Settings",
                ).mapIndexed { index, label -> SegmentOption(id = index, label = label) },
                selectedIndex = 0,
                onSegmentSelected = { },
                size = SegmentButtonSize.Medium,
            )

            // Large size
            SegmentButton(
                segments = listOf("All", "Active", "Completed").mapIndexed { index, label ->
                    SegmentOption(
                        id = index,
                        label = label,
                    )
                },
                selectedIndex = 2,
                onSegmentSelected = { },
                size = SegmentButtonSize.Large,
            )
        }
    }
}
