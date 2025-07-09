package com.greatergoods.meapp.features.common.components

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.reflect.KProperty1

/**
 * Represents the different sizes available for segment buttons.
 */
enum class SegmentButtonSize {
  Small, // Small segment button (height: 32dp)
  Medium, // Medium segment button (height: 40dp)
  Large, // Large segment button (height: 48dp)
}

/**
 * Represents the different types of segment button layouts.
 */
enum class SegmentButtonType {
  /** Single row layout without scrolling - uses SingleChoiceSegmentedButtonRow */
  Single,

  /** Multi-item scrollable layout - uses LazyRow for horizontal scrolling */
  Scrollable,
}

/*
* Segment button data
 */
data class SegmentButtonData(
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
  fun height(size: SegmentButtonSize): Dp =
    when (size) {
      SegmentButtonSize.Small -> 32.dp
      SegmentButtonSize.Medium -> 40.dp
      SegmentButtonSize.Large -> 48.dp
    }

  /**
   * Returns the minimum width for the given segment button size.
   */
  fun minWidth(size: SegmentButtonSize): Dp =
    when (size) {
      SegmentButtonSize.Small -> 0.dp
      SegmentButtonSize.Medium -> 120.dp
      SegmentButtonSize.Large -> 160.dp
      // TODO: Need to update after UX answered
    }

  /**
   * Horizontal spacing between segment buttons in LazyRow.
   */
  val segmentSpacing: Dp
    @Composable get() = MeTheme.spacing.lg

  /**
   * Returns the horizontal padding for the given segment button size.
   */
  @Composable
  fun horizontalPadding(size: SegmentButtonSize): Dp =
    when (size) {
      SegmentButtonSize.Small -> MeTheme.spacing.xs
      SegmentButtonSize.Medium -> MeTheme.spacing.sm
      SegmentButtonSize.Large -> MeTheme.spacing.sm
    }

  /**
   * Returns the text style for the given segment button size.
   */
  @Composable
  fun textStyle(size: SegmentButtonSize): TextStyle =
    when (size) {
      SegmentButtonSize.Small -> MeTheme.typography.link1
      SegmentButtonSize.Medium -> MeTheme.typography.button1
      SegmentButtonSize.Large -> MeTheme.typography.button1
    }

  /**
   * Returns the corner radius for the segment button container.
   */
  fun cornerRadius(): Dp = 8.dp // TODO: use design token for radius after pr merge

  @Composable
  fun colors(): SegmentedButtonColors =
    SegmentedButtonDefaults.colors(
      activeContainerColor = MeTheme.colorScheme.secondaryAction,
      inactiveContainerColor = Color.Transparent,
      activeBorderColor = MeTheme.colorScheme.secondaryAction,
      inactiveBorderColor = Color.Transparent,
      activeContentColor = MeTheme.colorScheme.inverseAction,
      inactiveContentColor = MeTheme.colorScheme.secondaryAction,
      // Todo: Update proper name after UX answered
    )
}

/**
 * A segmented button component that displays multiple options in a horizontal row,
 * allowing single selection similar to radio buttons.
 *
 * @param data List of segment labels to display
 * @param selectedIndex Index of the currently selected segment
 * @param onSelected Callback when a segment is selected
 * @param modifier Modifier to be applied to the component
 * @param size Size variant of the segment button
 * @param type Type of layout - Single (non-scrollable) or Scrollable (with LazyRow)
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun <T> SegmentButtonGroup(
  modifier: Modifier = Modifier,
  data: List<T>,
  selectedData: T,
  key: KProperty1<T, String>,
  contentPadding: PaddingValues = PaddingValues(0.dp),
  size: SegmentButtonSize = SegmentButtonSize.Small,
  type: SegmentButtonType = SegmentButtonType.Single,
  onSelected: (T) -> Unit,
) {
  SegmentButtonDefaults.minWidth(size)
  val horizontalPadding = SegmentButtonDefaults.horizontalPadding(size)
  val horizontalSpacedBy = SegmentButtonDefaults.segmentSpacing
  val colors = SegmentButtonDefaults.colors()
  val textStyle = SegmentButtonDefaults.textStyle(size)
  val shape = RoundedCornerShape(SegmentButtonDefaults.cornerRadius())
  val density = LocalDensity.current
  // val segmentButtonModifier = modifier.height(IntrinsicSize.Min)
  val maxLines = 1

  val listState = rememberLazyListState()
  val coroutineScope = rememberCoroutineScope()
  var calculatedItemWidthPx by remember { mutableIntStateOf(0) }
  var rowWidthPx by remember { mutableIntStateOf(0) }

  // Calculate center offset based on the dynamically calculated item width
  val centerOffsetPx =
    with(density) {
      (rowWidthPx - calculatedItemWidthPx) / 2f
    }

  LaunchedEffect(selectedData) {
    // Add calculatedItemWidthPx to dependencies
    if (data.isNotEmpty() && calculatedItemWidthPx > 0) { // Ensure width is calculated
      coroutineScope.launch {
        listState.animateScrollToItem(
          index = data.indexOf(selectedData),
          scrollOffset = -centerOffsetPx.roundToInt(),
        )
      }
    }
  }

  if (type == SegmentButtonType.Single) {
    // Single row layout - all buttons in one non-scrollable row
    SingleChoiceSegmentedButtonRow(
      modifier = modifier.fillMaxWidth(),
    ) {
      data.forEachIndexed { index, option ->
        SegmentedButton(
          shape = shape,
          onClick = { onSelected(option) },
          colors = colors,
          icon = {},
          selected = option == selectedData,
          label = {
            Text(
              text = key.get(option).uppercase(Locale.getDefault()),
              style = textStyle,
              modifier = Modifier
                .padding(horizontal = horizontalPadding)
                .wrapContentWidth(unbounded = true), // allows growing wider
              softWrap = false,
              overflow = TextOverflow.Visible,
              maxLines = maxLines,
            )
          },
          modifier = Modifier.weight(1f),
        )
      }
    }
  } else {
    // Scrollable layout - horizontal scrolling with LazyRow
    LazyRow(
      state = listState,
      contentPadding = contentPadding,
      horizontalArrangement = Arrangement.spacedBy(horizontalSpacedBy, Alignment.Start),
      modifier =
        modifier
          .onSizeChanged { rowWidthPx = it.width },
    ) {
      itemsIndexed(data) { index, option ->
        SingleChoiceSegmentedButtonRow(
          modifier =
            Modifier
              .onSizeChanged {
                // This calculates the width of the current segment button.
                // You might want to take the max width of all items if they vary.
                // For simplicity, we'll just set it for the first one encountered or update if a larger one is found.
                if (it.width > calculatedItemWidthPx) {
                  calculatedItemWidthPx = it.width
                }
              },
        ) {
          SegmentedButton(
            shape = shape,
            onClick = { onSelected(option) },
            colors = colors,
            icon = {},
            selected = option == selectedData,
            label = {
              Text(
                text = key.get(option).uppercase(Locale.getDefault()),
                style = textStyle,
                modifier = Modifier
                  .padding(horizontal = horizontalPadding)
                  .wrapContentWidth(unbounded = true), // allows growing wider
                softWrap = false,
                overflow = TextOverflow.Visible,
                maxLines = maxLines,
              )
            },
            modifier =
              modifier
                .onSizeChanged {
                  // This calculates the width of the current segment button.
                  // You might want to take the max width of all items if they vary.
                  // For simplicity, we'll just set it for the first one encountered or update if a larger one is found.
                  if (it.width > calculatedItemWidthPx) {
                    calculatedItemWidthPx = it.width
                  }
                },
          )
        }
      }
    }
  }
}

/**
 * Preview for different segment button configurations.
 */
@PreviewTheme
@Composable
fun SegmentButtonPreview() {
  MeAppTheme {
    Column(
      modifier = Modifier.padding(MeTheme.spacing.md),
      verticalArrangement = Arrangement.spacedBy(MeTheme.spacing.lg),
    ) {
      val sampleSmallData =
        listOf("Day", "Week", "Month").mapIndexed { index, label ->
          SegmentButtonData(id = index, label = label)
        }
      val sampleMediumData =
        listOf(
          "Overview",
          "Details",
          "Settings",
          "Profile",
          "Weight",
          "Height",
          "Activity",
        ).mapIndexed { index, label ->
          SegmentButtonData(id = index, label = label)
        }
      val sampleLargeData =
        listOf(
          "Day",
          "Week",
          "Month",
          "Overview",
          "Details",
          "Settings",
          "Profile",
          "Weight",
          "Height",
          "Activity",
        ).mapIndexed { index, label ->
          SegmentButtonData(id = index, label = label)
        }

      var selectedSmallData by remember { mutableStateOf(sampleSmallData[0]) }
      var selectedMediumData by remember { mutableStateOf(sampleMediumData[0]) }
      var selectedLargeData by remember { mutableStateOf(sampleLargeData[0]) }
      // --- Single Type - Small size ---
      SegmentButtonGroup(
        data =
          sampleSmallData,
        key = SegmentButtonData::label,
        selectedData = selectedSmallData,
        onSelected = { selectedSmallData = it },
        size = SegmentButtonSize.Small,
      )

      // --- Scrollable Type - Medium size ---
      SegmentButtonGroup(
        data =
          sampleMediumData,
        key = SegmentButtonData::label,
        selectedData = selectedMediumData,
        onSelected = { selectedMediumData = it },
        size = SegmentButtonSize.Medium,
      )

      // --- Single Type - Large size ---
      SegmentButtonGroup(
        data =
          sampleLargeData,
        key = SegmentButtonData::label,
        selectedData = selectedLargeData,
        onSelected = { selectedLargeData = it },
        size = SegmentButtonSize.Large,
      )

      // --- Scrollable Type - Many items ---
      SegmentButtonGroup(
        data = sampleLargeData,
        key = SegmentButtonData::label,
        selectedData = selectedLargeData,
        onSelected = { selectedLargeData = it },
        size = SegmentButtonSize.Small,
        type = SegmentButtonType.Scrollable,
      )
    }
  }
}
