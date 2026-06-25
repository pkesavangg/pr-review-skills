package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import java.util.Locale

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
   * Primary color for selected segment buttons.
   */
  val PickerCyan: Color = Color(0xFF47B0EC)

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
      SegmentButtonSize.Medium -> 80.dp
      SegmentButtonSize.Large -> 100.dp
    }

  /**
   * Horizontal spacing between segment buttons in LazyRow.
   */
  val segmentSpacing: Dp
    @Composable get() = MeTheme.spacing.xs

  /**
   * Returns the horizontal padding for the given segment button size.
   */
  @Composable
  fun horizontalPadding(size: SegmentButtonSize): Dp =
    when (size) {
      SegmentButtonSize.Small -> MeTheme.spacing.sm
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
  fun cornerRadius(): Dp = 8.dp

  @Composable
  fun colors(): SegmentedButtonColors =
    SegmentedButtonDefaults.colors(
      activeContainerColor = MeTheme.colorScheme.secondaryAction,
      inactiveContainerColor = Color.Transparent,
      activeBorderColor = MeTheme.colorScheme.secondaryAction,
      inactiveBorderColor = Color.Transparent,
      activeContentColor = MeTheme.colorScheme.inverseAction,
      inactiveContentColor = MeTheme.colorScheme.secondaryAction,
    )
}

/**
 * A segmented button component that displays multiple options in a horizontal row,
 * allowing single selection similar to radio buttons.
 *
 * @param data List of segment labels to display
 * @param selectedData Currently selected data item
 * @param key Property reference to extract display text from data items
 * @param onSelected Callback when a segment is selected
 * @param modifier Modifier to be applied to the component
 * @param contentPadding Padding for scrollable content
 * @param size Size variant of the segment button
 * @param type Type of layout - Single (non-scrollable) or Scrollable (with LazyRow)
 */
@Composable
fun <T> SegmentButtonGroup(
  modifier: Modifier = Modifier,
  data: List<T>,
  selectedData: T,
  key: (T) -> String,
  contentPadding: PaddingValues = PaddingValues(0.dp),
  size: SegmentButtonSize = SegmentButtonSize.Small,
  type: SegmentButtonType = SegmentButtonType.Single,
  spacedBy: Dp = 0.dp,
  // Labels upper-cased by default (Day/Week/Month tabs); unit toggles (lbs/kg, ft·in/cm) pass false.
  uppercaseLabels: Boolean = true,
  onSelected: (T) -> Unit,
) {
  val textStyle = SegmentButtonDefaults.textStyle(size)
  val horizontalPadding = SegmentButtonDefaults.horizontalPadding(size)
  val cornerRadius = SegmentButtonDefaults.cornerRadius()
  val listState = rememberLazyListState()

  AutoCenterSelected(listState, data, selectedData, key, enabled = type == SegmentButtonType.Scrollable)

  if (type == SegmentButtonType.Single) {
    Row(
      modifier = modifier
        .fillMaxWidth()
        .padding(horizontal = MeTheme.spacing.xs),
      horizontalArrangement = Arrangement.SpaceAround,
    ) {
      data.forEach { item ->
        SegmentButtonItem(
          item = item,
          isSelected = key(selectedData) == key(item),
          key = key,
          textStyle = textStyle,
          horizontalPadding = horizontalPadding,
          cornerRadius = cornerRadius,
          maxLines = 1,
          uppercaseLabels = uppercaseLabels,
          onSelected = onSelected,
          modifier = Modifier.width(intrinsicSize = IntrinsicSize.Max), // Take natural content width
        )
      }
    }
  } else {
    LazyRow(
      state = listState,
      contentPadding = contentPadding,
      horizontalArrangement = Arrangement.spacedBy(spacedBy),
      modifier = modifier,
    ) {
      items(
        items = data,
        key = { item -> key(item) }, // Use stable keys for better performance
      ) { item ->
        SegmentButtonItem(
          item = item,
          isSelected = key(selectedData) == key(item),
          key = key,
          textStyle = textStyle,
          horizontalPadding = horizontalPadding,
          cornerRadius = cornerRadius,
          maxLines = 1,
          uppercaseLabels = uppercaseLabels,
          onSelected = onSelected,
          modifier = Modifier.width(intrinsicSize = IntrinsicSize.Max), // Maintain intrinsic width for scrollable
        )
      }
    }
  }
}

/** Auto-scrolls the [listState] so the selected item is centered (scrollable layout only). */
@Composable
private fun <T> AutoCenterSelected(
  listState: LazyListState,
  data: List<T>,
  selectedData: T,
  key: (T) -> String,
  enabled: Boolean,
) {
  LaunchedEffect(selectedData) {
    if (data.isNotEmpty() && enabled) {
      val selectedIndex = data.indexOfFirst { key(it) == key(selectedData) }
      if (selectedIndex >= 0) listState.animateScrollToItemCenter(selectedIndex)
    }
  }
}

/**
 * Individual segment button item with custom styling and animation.
 */
@Composable
private fun <T> SegmentButtonItem(
  item: T,
  isSelected: Boolean,
  key: (T) -> String,
  textStyle: TextStyle,
  horizontalPadding: Dp,
  cornerRadius: Dp,
  maxLines: Int,
  uppercaseLabels: Boolean,
  onSelected: (T) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = SegmentButtonDefaults.colors()

  Box(
    modifier = modifier
      .height(intrinsicSize = IntrinsicSize.Max)
      // Exposes active segment as `selected` for E2E (MOB-399). Metadata-only.
      .semantics { selected = isSelected }
      .clickable(
        indication = null,
        interactionSource = remember { MutableInteractionSource() },
      ) {
        onSelected(item)
      },
    contentAlignment = Alignment.Center,
  ) {

    if (isSelected) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .clip(RoundedCornerShape(cornerRadius))
          .background(colors.activeContainerColor),
      )
    }

    // Text content above the animated background
    Row(
      modifier = Modifier
        .padding(
          horizontal = horizontalPadding,
          vertical = 8.dp,
        ),
    ) {
      Text(
        text = if (uppercaseLabels) key(item).uppercase(Locale.getDefault()) else key(item),
        style = textStyle,
        color = if (isSelected) colors.activeContentColor else colors.inactiveContentColor,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.basicMarquee(
          iterations = Int.MAX_VALUE, // Infinite scrolling
        ),
      )
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
        listOf("Day", "Week", "Month", "Year").mapIndexed { index, label ->
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

      val sampleLongTextData =
        listOf(
          "Very Long Text",
          "Extremely Long Button Text",
          "Super Long Text Content",
          "Maximum Length Text",
        ).mapIndexed { index, label ->
          SegmentButtonData(id = index, label = label)
        }

      var selectedSmallData by remember { mutableStateOf(sampleSmallData[0]) }
      var selectedMediumData by remember { mutableStateOf(sampleMediumData[0]) }
      var selectedLargeData by remember { mutableStateOf(sampleLargeData[0]) }
      var selectedLongTextData by remember { mutableStateOf(sampleLongTextData[0]) }
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

      // --- Single Type - Long text test ---
      SegmentButtonGroup(
        data = sampleLongTextData,
        key = SegmentButtonData::label,
        selectedData = selectedLongTextData,
        onSelected = { selectedLongTextData = it },
        size = SegmentButtonSize.Medium,
        type = SegmentButtonType.Single,
      )
    }
  }
}

suspend fun LazyListState.animateScrollToItemCenter(index: Int) {
  layoutInfo.resolveItemOffsetToCenter(index)?.let {
    animateScrollToItem(index, it)
    return
  }

  scrollToItem(index)

  layoutInfo.resolveItemOffsetToCenter(index)?.let {
    animateScrollToItem(index, it)
  }
}

private fun LazyListLayoutInfo.resolveItemOffsetToCenter(index: Int): Int? {
  val itemInfo = visibleItemsInfo.firstOrNull { it.index == index } ?: return null
  val containerSize = viewportSize.width - beforeContentPadding - afterContentPadding
  return -(containerSize - itemInfo.size) / 2
}
