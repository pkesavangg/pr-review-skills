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
import com.dmdbrands.gurus.weight.features.common.enums.DeviceSegmentType
import com.dmdbrands.gurus.weight.features.common.enums.DeviceSetupType
import com.dmdbrands.gurus.weight.features.common.model.DEVICES
import com.dmdbrands.gurus.weight.features.common.model.DeviceModelInfo
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

/**
 * Composable for the scale segment selector (All, Bluetooth, Wifi) using SegmentButtonGroup.
 * Displays a lazy-loaded filtered list of AppDeviceCard based on the selected segment for optimal performance.
 *
 * @param onScaleSelected Callback when a scale is selected from the list.
 * @param modifier Modifier to be applied to the component.
 * @param enableLazyLoading Whether to use lazy loading for the scale list (default: true).
 * @param lazyListState Optional LazyListState for external scroll control.
 * @param initialSelectedType Initial segment type to display (default: All).
 */
@Composable
fun DeviceList(
  onScaleSelected: (DeviceModelInfo) -> Unit,
  modifier: Modifier = Modifier,
  enableLazyLoading: Boolean = true,
  lazyListState: LazyListState = rememberLazyListState(),
  initialSelectedType: DeviceSegmentType = DeviceSegmentType.All,
  header: (@Composable () -> Unit)? = null,  // NEW
  footer: (@Composable () -> Unit)? = null,  // NEW
) {
  var selectedType by remember { mutableStateOf(initialSelectedType) }

  val devices = DEVICES
  val filteredScales = remember(selectedType) {
    when (selectedType) {
      DeviceSegmentType.All -> devices
      DeviceSegmentType.AppSync ->
        devices.filter {
          it.setupType == DeviceSetupType.AppSync
        }

      DeviceSegmentType.Bluetooth ->
        devices.filter {
          it.setupType == DeviceSetupType.Bluetooth ||
            it.setupType == DeviceSetupType.Lcbt ||
            it.setupType == DeviceSetupType.BtWifiR4 ||
            it.setupType == DeviceSetupType.BpmBluetooth ||
            it.setupType == DeviceSetupType.BpmA6Bluetooth
        }

      DeviceSegmentType.Wifi ->
        devices.filter {
          it.setupType == DeviceSetupType.Wifi ||
            it.setupType == DeviceSetupType.EspTouchWifi ||
            it.setupType == DeviceSetupType.BtWifiR4
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
            DeviceSegmentType.All,
            DeviceSegmentType.Bluetooth,
            DeviceSegmentType.Wifi,
            DeviceSegmentType.AppSync,
          ),
          key = DeviceSegmentType::name,
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
      // Combine sku + setupType because some SKUs (e.g. 0603, 0634, 0661, 0663)
      // appear in both SCALES and MONITORS with different setup types.
      key = { scale -> "${scale.sku}-${scale.setupType.name}" },
    ) { scale ->
      AppDeviceCard(
        scale = scale,
        isSavedScale = false,
        onClick = onScaleSelected,
      )
    }

    footer?.let { item(key="footer") { it() } }
  }
}

/**
 * Preview for DeviceList composable with all segments and lazy loading.
 */
@PreviewTheme
@Composable
fun PreviewScaleList() {
  MeAppTheme {
    DeviceList(
      onScaleSelected = { scale ->
        // Handle scale selection in preview
      },
      enableLazyLoading = true,
    )
  }
}

/**
 * Preview for DeviceList composable without lazy loading (for comparison).
 */
@PreviewTheme
@Composable
fun PreviewScaleListNoLazy() {
  MeAppTheme {
    DeviceList(
      onScaleSelected = { scale ->
        // Handle scale selection in preview
      },
      enableLazyLoading = false,
    )
  }
}
