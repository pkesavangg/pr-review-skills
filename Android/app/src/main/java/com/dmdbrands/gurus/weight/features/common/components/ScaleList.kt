package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSegmentType
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.gurus.weight.features.common.model.SCALES
import com.dmdbrands.gurus.weight.features.common.model.ScaleInfo
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

/**
 * Composable for the scale segment selector (All, Bluetooth, Wifi) using SegmentButtonGroup.
 * Displays a lazy-loaded filtered list of AppScaleCard based on the selected segment for optimal performance.
 *
 * @param onScaleSelected Callback when a scale is selected from the list.
 * @param modifier Modifier to be applied to the component.
 * @param enableLazyLoading Whether to use lazy loading for the scale list (default: true).
 * @param lazyListState Optional LazyListState for external scroll control.
 * @param initialSelectedType Initial segment type to display (default: All).
 */
@Composable
fun ScaleList(
  onScaleSelected: (ScaleInfo) -> Unit,
  modifier: Modifier = Modifier,
  enableLazyLoading: Boolean = true,
  lazyListState: LazyListState = rememberLazyListState(),
  initialSelectedType: ScaleSegmentType = ScaleSegmentType.All,
  header: (@Composable () -> Unit)? = null,  // NEW
  footer: (@Composable () -> Unit)? = null,  // NEW
) {
  var selectedType by remember { mutableStateOf(initialSelectedType) }

  val filteredScales = remember(selectedType) {
    when (selectedType) {
      ScaleSegmentType.All -> SCALES
      ScaleSegmentType.AppSync ->
        SCALES.filter {
          it.setupType == ScaleSetupType.AppSync
        }

      ScaleSegmentType.Bluetooth ->
        SCALES.filter {
          it.setupType == ScaleSetupType.Bluetooth ||
            it.setupType == ScaleSetupType.Lcbt ||
            it.setupType == ScaleSetupType.BtWifiR4
        }

      ScaleSegmentType.Wifi ->
        SCALES.filter {
          it.setupType == ScaleSetupType.Wifi ||
            it.setupType == ScaleSetupType.EspTouchWifi ||
            it.setupType == ScaleSetupType.BtWifiR4
        }
    }
  }

  // Reset scroll position when segment changes for better UX
  LaunchedEffect(selectedType) {
    if (lazyListState.firstVisibleItemIndex > 0) {
      lazyListState.animateScrollToItem(0)
    }
  }
  LazyColumn(
    state = lazyListState,
    modifier = modifier
      .fillMaxWidth(),
  ) {
    header?.let { item(key = "header") { it() } }
    // Header item - Segment control
    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = spacing.sm, end = spacing.sm, bottom = spacing.sm),
      ) {
        SegmentButtonGroup(
          data = listOf(
            ScaleSegmentType.All,
            ScaleSegmentType.Bluetooth,
            ScaleSegmentType.Wifi,
            ScaleSegmentType.AppSync,
          ),
          key = ScaleSegmentType::name,
          selectedData = selectedType,
          onSelected = { selectedType = it },
          size = SegmentButtonSize.Small,
          type = SegmentButtonType.Scrollable,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }

    items(
      items = filteredScales,
      key = { scale -> scale.sku }, // Use sku as unique key for better performance
    ) { scale ->
      AppScaleCard(
        scale = scale,
        isSavedScale = false,
        onClick = onScaleSelected,
      )
    }

    footer?.let { item(key="footer") { it() } }
  }
}

/**
 * Preview for ScaleList composable with all segments and lazy loading.
 */
@PreviewTheme
@Composable
fun PreviewScaleList() {
  MeAppTheme {
    ScaleList(
      onScaleSelected = { scale ->
        // Handle scale selection in preview
      },
      enableLazyLoading = true,
    )
  }
}

/**
 * Preview for ScaleList composable without lazy loading (for comparison).
 */
@PreviewTheme
@Composable
fun PreviewScaleListNoLazy() {
  MeAppTheme {
    ScaleList(
      onScaleSelected = { scale ->
        // Handle scale selection in preview
      },
      enableLazyLoading = false,
    )
  }
}
