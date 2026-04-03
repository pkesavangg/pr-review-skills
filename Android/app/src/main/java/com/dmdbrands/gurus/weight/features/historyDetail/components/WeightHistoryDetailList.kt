package com.dmdbrands.gurus.weight.features.historyDetail.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryMetricEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntryWithMetrics
import com.dmdbrands.gurus.weight.features.common.components.AppSwipeableActionItem
import com.dmdbrands.gurus.weight.features.common.components.AppSwipeableList
import com.dmdbrands.gurus.weight.features.common.components.AppSwipeableListActions
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.historyDetail.strings.HistoryDetailScreenStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * List of history detail items, using WeightHistoryDetailItem for each row.
 * @param historyDetails List of history detail item models
 * @param itemsOpened List of opened item IDs
 * @param onItemsOpen Callback when items are opened/closed
 * @param onItemDelete Callback when an item's "Delete" is clicked
 */
@Composable
fun WeightHistoryDetailList(
  historyDetails: List<ScaleEntry>,
  itemsOpened: List<Long> = emptyList(),
  onItemsOpen: (List<Long>) -> Unit,
  onItemDelete: (ScaleEntry) -> Unit,
) {
  // Create a derived state to force recomposition when itemsOpened changes
  val expandedItems by remember(itemsOpened) {
    derivedStateOf { itemsOpened.toSet() }
  }


  AppSwipeableList(
    items = historyDetails,
    iconWidth = 88.dp,
    keySelector = { it.entry.id },
    trailingActions = { index, item ->
      AppSwipeableListActions {
        AppSwipeableActionItem(
          itemWidth = 88.dp,
          text = HistoryDetailScreenStrings.DeleteButton,
          contentDescription = HistoryDetailScreenStrings.DeleteEntryContentDescription,
          backgroundColor = MeTheme.colorScheme.textError,
        ) {
          onItemDelete(item)
        }
      }
    },
  ) { item ->
    WeightHistoryDetailItem(
      item = item,
      isExpanded = expandedItems.contains(item.entry.id),
      onItemOpen = { itemId ->
        val newItemsOpened = if (itemsOpened.contains(itemId)) {
          itemsOpened.filter { it != itemId }
        } else {
          itemsOpened + itemId
        }
        onItemsOpen(newItemsOpened)
      },
    )
  }
}

@PreviewTheme
@Composable
fun HistoryDetailListPreview() {
  MeAppTheme {
    val sampleItems =
      listOf(
        ScaleEntry(
          entry = EntryEntity(
            id = 478,
            accountId = "4SWOWDAP9t2gS50MFp9HQS",
            entryTimestamp = "2025-06-19T06:30:00.000Z",
            serverTimestamp = "2025-06-19T10:29:13.914Z",
            opTimestamp = null,
            operationType = "create",
            deviceType = "scale",
            deviceId = "manual",
            attempts = 0,
            unit = WeightUnit.LB,
            isSynced = true,
          ),
          scale = ScaleEntryWithMetrics(
            scaleEntry = BodyScaleEntryEntity(
              id = 478,
              weight = 150.0,
              bodyFat = 15.2,
              muscleMass = 35.0,
              water = 55.0,
              bmi = 22.5,
              source = "manual",
            ),
            scaleEntryMetric = BodyScaleEntryMetricEntity(
              id = 478,
              bmr = 1800.0,
              metabolicAge = 28,
              proteinPercent = 18.0,
              pulse = 60,
              skeletalMusclePercent = 52.7,
              subcutaneousFatPercent = 10.3,
              visceralFatLevel = 8.0,
              boneMass = 4.4,
              impedance = 500,
            ),
          ),
        ),
        ScaleEntry(
          entry = EntryEntity(
            id = 479,
            accountId = "4SWOWDAP9t2gS50MFp9HQS",
            entryTimestamp = "2025-06-20T06:30:00.000Z",
            serverTimestamp = "2025-06-20T10:29:13.914Z",
            opTimestamp = null,
            operationType = "create",
            deviceType = "scale",
            deviceId = "manual",
            attempts = 0,
            unit = WeightUnit.KG,
            isSynced = true,
          ),
          scale = ScaleEntryWithMetrics(
            scaleEntry = BodyScaleEntryEntity(
              id = 479,
              weight = 70.0,
              bodyFat = 14.0,
              muscleMass = 33.0,
              water = 58.0,
              bmi = 21.5,
              source = "manual",
            ),
            scaleEntryMetric = BodyScaleEntryMetricEntity(
              id = 479,
              bmr = 1700.0,
              metabolicAge = 27,
              proteinPercent = 19.0,
              pulse = 65,
              skeletalMusclePercent = 50.1,
              subcutaneousFatPercent = 9.5,
              visceralFatLevel = 7.0,
              boneMass = 4.1,
              impedance = 510,
            ),
          ),
        ),
      )
    WeightHistoryDetailList(
      historyDetails = sampleItems,
      itemsOpened = emptyList(),
      onItemsOpen = {},
      onItemDelete = {},
    )
  }
}
