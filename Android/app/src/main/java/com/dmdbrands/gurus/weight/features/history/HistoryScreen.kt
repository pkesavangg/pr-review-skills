package com.dmdbrands.gurus.weight.features.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.dmdbrands.gurus.weight.core.shared.utilities.testing.TestTags
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.model.common.HistoryMonth
import com.dmdbrands.gurus.weight.features.common.components.AppIcon
import com.dmdbrands.gurus.weight.features.common.components.AppIconType
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.BabyEmptyState
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.ProductTypeHeader
import com.dmdbrands.gurus.weight.features.common.components.strings.BabyEmptyStateStrings
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.services.IProductSelectionManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState2
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.components.SnapshotColors
import com.dmdbrands.gurus.weight.features.history.components.BabyHistoryList
import com.dmdbrands.gurus.weight.features.history.components.BpHistoryList
import com.dmdbrands.gurus.weight.features.history.components.ProductHistoryEmptyState
import com.dmdbrands.gurus.weight.features.history.components.WeightHistoryList
import com.dmdbrands.gurus.weight.features.history.strings.HistoryEmptyStateStrings
import com.dmdbrands.gurus.weight.features.history.strings.HistoryScreenStrings
import com.dmdbrands.gurus.weight.features.history.viewmodel.HistoryIntent
import com.dmdbrands.gurus.weight.features.history.viewmodel.HistoryState
import com.dmdbrands.gurus.weight.features.history.viewmodel.HistoryViewModel
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen() {
  val viewModel: HistoryViewModel = hiltViewModel()
  val state by viewModel.state.collectAsStateWithLifecycle()
  val isRefreshing = state.isLoading

  // Re-query on resume so returning to History (e.g. after deleting the last entry of a day) shows
  // the current data — the entry_view Room flows don't reliably re-emit after the delete. (MOB-1173)
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }
  HistoryScreenContent(
    state = state,
    productSelectionManager = viewModel.productSelectionManager,
    isRefreshing = isRefreshing,
    onRefresh = {
      viewModel.handleIntent(HistoryIntent.Refresh)
    },
    handleIntent = viewModel::handleIntent,
  )
}

@Composable
fun HistoryScreenContent(
  state: HistoryState,
  productSelectionManager: IProductSelectionManager? = null,
  isRefreshing: Boolean = false,
  onRefresh: (() -> Unit)? = null,
  handleIntent: (HistoryIntent) -> Unit,
) {
  val navBackStack = LocalNavBackStack.current
  val coroutineScope = rememberCoroutineScope()
  val selectedProduct = productSelectionManager?.selectedProduct
      ?.collectAsState2()
  val hasMultipleProducts = (productSelectionManager?.availableProducts
      ?.collectAsState2()?.value?.size ?: 0) > 1

  // Export is enabled when the ACTIVE product has history to export — each product keeps its
  // own list, so keying off the weight list alone left Balance/Baby exports disabled (MOB-1230).
  val activeSelection = selectedProduct?.value ?: ProductSelection.MyWeight
  val canExport = when (activeSelection) {
    is ProductSelection.MyWeight -> state.historyItems.isNotEmpty()
    is ProductSelection.BloodPressure -> state.bpHistoryItems.isNotEmpty()
    is ProductSelection.Baby -> state.babyHistoryItems[activeSelection.profile.id]?.isNotEmpty() == true
    is ProductSelection.BabyScale -> false
  }

  AppScaffold(
    title = if (productSelectionManager == null) HistoryScreenStrings.Title else null,
    topBarContent = if (productSelectionManager != null) {
      {
        ProductTypeHeader(
          selectedProduct = selectedProduct?.value,
          onClick = { productSelectionManager.showProductSheet(HistoryScreenStrings.Title) },
          showDropdown = hasMultipleProducts,
        )
      }
    } else null,
    actions = {
      AppIcon(
        id = AppIcons.Default.Export,
        contentDescription = HistoryScreenStrings.ExportDataDescription,
        type = AppIconType.Primary,
        modifier = Modifier
          .padding(end = spacing.sm)
          .testTag(TestTags.History.DownloadButton),
        enabled = canExport,
      ) {
        handleIntent(HistoryIntent.Export)
      }
    },
    isRefreshing = state.isLoading,
    onRefresh = onRefresh,
  ) { modifier ->
    Box(modifier = modifier.fillMaxSize()) {
      val currentProduct = selectedProduct?.value ?: ProductSelection.MyWeight
      when (currentProduct) {
        is ProductSelection.MyWeight -> {
          if (state.historyItems.isEmpty()) {
            if (state.hasWeightDevice) {
              ProductHistoryEmptyState(
                icon = AppIcons.Default.History,
                iconTint = SnapshotColors.Weight,
                iconContentDescription = HistoryEmptyStateStrings.NoEntriesIconDescription,
                title = HistoryEmptyStateStrings.WeightNoEntryTitle,
                description = HistoryEmptyStateStrings.WeightNoEntryDescription,
                primaryLabel = HistoryEmptyStateStrings.LogManually,
                onPrimaryClick = { handleIntent(HistoryIntent.OnLogManually) },
              )
            } else {
              ProductHistoryEmptyState(
                icon = AppIcons.Default.WeightScale,
                iconTint = SnapshotColors.Weight,
                iconContentDescription = HistoryEmptyStateStrings.WeightIconDescription,
                title = HistoryEmptyStateStrings.WeightNoDeviceTitle,
                description = HistoryEmptyStateStrings.WeightNoDeviceDescription,
                primaryLabel = HistoryEmptyStateStrings.AddDevice,
                onPrimaryClick = { handleIntent(HistoryIntent.OnConnectScale) },
              )
            }
          } else {
            WeightHistoryList(
              items = state.historyItems,
              onItemClick = { item ->
                if (item.entryTimestamp != null) {
                  coroutineScope.launch {
                    navBackStack.addRoute(
                      AppRoute.History.MonthDetails(item.entryTimestamp, ProductType.MY_WEIGHT),
                    )
                  }
                }
              },
            )
          }
        }

        is ProductSelection.BloodPressure -> {
          if (state.bpHistoryItems.isEmpty()) {
            if (state.hasBpmDevice) {
              ProductHistoryEmptyState(
                icon = AppIcons.Default.History,
                iconTint = SnapshotColors.BloodPressure,
                iconContentDescription = HistoryEmptyStateStrings.NoEntriesIconDescription,
                title = HistoryEmptyStateStrings.BpmNoEntryTitle,
                description = HistoryEmptyStateStrings.BpmNoEntryDescription,
                primaryLabel = HistoryEmptyStateStrings.LogManually,
                onPrimaryClick = { handleIntent(HistoryIntent.OnLogManually) },
              )
            } else {
              ProductHistoryEmptyState(
                icon = AppIcons.Default.BloodPressureMonitor,
                iconTint = SnapshotColors.BloodPressure,
                iconContentDescription = HistoryEmptyStateStrings.BpmIconDescription,
                title = HistoryEmptyStateStrings.BpmNoDeviceTitle,
                description = HistoryEmptyStateStrings.BpmNoDeviceDescription,
                primaryLabel = HistoryEmptyStateStrings.AddDevice,
                onPrimaryClick = { handleIntent(HistoryIntent.OnConnectScale) },
              )
            }
          } else {
            BpHistoryList(
              items = state.bpHistoryItems,
              onItemClick = { item ->
                coroutineScope.launch {
                  navBackStack.addRoute(
                    AppRoute.History.MonthDetails(item.entryTimestamp, ProductType.BLOOD_PRESSURE),
                  )
                }
              },
            )
          }
        }

        is ProductSelection.Baby -> {
          val babyGroups = state.babyHistoryItems[currentProduct.profile.id] ?: persistentListOf()
          if (babyGroups.isEmpty()) {
            if (state.hasBabyDevice) {
              ProductHistoryEmptyState(
                icon = AppIcons.Default.History,
                iconTint = SnapshotColors.Baby,
                iconContentDescription = HistoryEmptyStateStrings.NoEntriesIconDescription,
                title = HistoryEmptyStateStrings.BabyNoEntryTitle,
                description = HistoryEmptyStateStrings.BabyNoEntryDescription,
                primaryLabel = HistoryEmptyStateStrings.LogManually,
                onPrimaryClick = { handleIntent(HistoryIntent.OnLogManually) },
              )
            } else {
              ProductHistoryEmptyState(
                icon = AppIcons.Default.BabyScale,
                iconTint = SnapshotColors.Baby,
                iconContentDescription = HistoryEmptyStateStrings.BabyScaleIconDescription,
                title = HistoryEmptyStateStrings.BabyNoDeviceTitle,
                description = HistoryEmptyStateStrings.BabyNoDeviceDescription,
                primaryLabel = HistoryEmptyStateStrings.AddDevice,
                onPrimaryClick = { handleIntent(HistoryIntent.OnConnectScale) },
              )
            }
          } else {
            BabyHistoryList(
              groups = babyGroups,
              birthdate = (currentProduct as? ProductSelection.Baby)?.profile?.birthdate,
              onItemClick = {  item ->
                coroutineScope.launch {
                navBackStack.addRoute(
                  AppRoute.History.MonthDetails(item.dateKey, ProductType.BABY),
                )
              }},
            )
          }
        }

        is ProductSelection.BabyScale -> {
          // No baby profile yet: Manual Entry and History both show the add-a-baby empty
          // state (baby icon + "No babies added yet"), under the "Baby Scale" title. (MOB-592)
          BabyEmptyState(
            onAddBaby = {
              coroutineScope.launch {
                navBackStack.addRoute(AppRoute.AccountSettings.AddBaby())
              }
            },
            description = BabyEmptyStateStrings.EntryDescription,
          )
        }
      }
    }
  }
}

@PreviewTheme
@Composable
fun HistoryScreenPreview() {
  MeAppTheme {
    val sampleItems =
      listOf(
        HistoryMonth(
          entryTimestamp = "2023-10",
          avgWeight = 70.5,
          entryCount = 15,
          change = -1.2,
        ),
        HistoryMonth(
          entryTimestamp = "2023-09",
          avgWeight = 71.0,
          entryCount = 12,
          change = 0.5,
        ),
        HistoryMonth(
          entryTimestamp = "2023-08",
          avgWeight = 72.0,
          entryCount = 10,
          change = -0.8,
        ),
      )
    HistoryScreenContent(
      state = HistoryState(historyItems = sampleItems.toImmutableList()),
      isRefreshing = false,
      onRefresh = {},
      handleIntent = {},
    )
  }
}
