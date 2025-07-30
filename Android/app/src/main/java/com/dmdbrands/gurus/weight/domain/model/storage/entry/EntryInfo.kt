package com.dmdbrands.gurus.weight.domain.model.storage.entry

import androidx.room.Embedded
import androidx.room.Relation
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.ActiveEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BaseEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryMetricEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BpmEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.convertToDisplay

interface EntryInfo<T : BaseEntryEntity> {
  val entry: T
  val bpmEntry: BpmEntryEntity?
  val scaleEntry: BodyScaleEntryEntity?
  val scaleEntryMetric: BodyScaleEntryMetricEntity?
  fun toEntry(convertToDisplay: Boolean = true): Entry? {
    val entryEntity = EntryEntity(
      id = entry.id,
      accountId = entry.accountId,
      unit = entry.unit,
      operationType = entry.operationType,
      entryTimestamp = entry.entryTimestamp,
      opTimestamp = entry.opTimestamp,
      isSynced = entry.isSynced,
      serverTimestamp = entry.serverTimestamp,
      deviceType = entry.deviceType,
      deviceId = entry.deviceId,
      attempts = entry.attempts,
    )

    val bpmEntry = this.bpmEntry
    val scaleEntry = this.scaleEntry
    val scaleEntryMetric = this.scaleEntryMetric
    return when {
      bpmEntry != null -> BpmEntry(
        entry = entryEntity,
        bpmEntry = bpmEntry,
      )

      scaleEntry != null -> ScaleEntry(
        entry = entryEntity,
        scale = ScaleEntryWithMetrics(
          scaleEntry = if (convertToDisplay) scaleEntry.convertToDisplay() else scaleEntry,
          scaleEntryMetric = if (convertToDisplay) scaleEntryMetric?.convertToDisplay() else scaleEntryMetric,
        ),
      )

      else -> {
        // Log a warning for debugging
        com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog.w(
          "EntryInfo",
          "toEntry: both bpmEntry and scaleEntry are null for id=${entry.id}",
        )
        null
      }
    }
  }
}

/**
 * Concrete class for Room: PopulatedEntry (EntryEntity)
 */
data class PopulatedEntry(
  @Embedded override val entry: EntryEntity,
  @Relation(parentColumn = "id", entityColumn = "id")
  override val bpmEntry: BpmEntryEntity?,
  @Relation(parentColumn = "id", entityColumn = "id")
  override val scaleEntry: BodyScaleEntryEntity?,
  @Relation(parentColumn = "id", entityColumn = "id")
  override val scaleEntryMetric: BodyScaleEntryMetricEntity?
) : EntryInfo<EntryEntity>

/**
 * Concrete class for Room: PopulatedActiveEntry (ActiveEntryEntity)
 */
data class PopulatedActiveEntry(
  @Embedded override val entry: ActiveEntryEntity,
  @Relation(parentColumn = "id", entityColumn = "id")
  override val bpmEntry: BpmEntryEntity?,
  @Relation(parentColumn = "id", entityColumn = "id")
  override val scaleEntry: BodyScaleEntryEntity?,
  @Relation(parentColumn = "id", entityColumn = "id")
  override val scaleEntryMetric: BodyScaleEntryMetricEntity?
) : EntryInfo<ActiveEntryEntity>
