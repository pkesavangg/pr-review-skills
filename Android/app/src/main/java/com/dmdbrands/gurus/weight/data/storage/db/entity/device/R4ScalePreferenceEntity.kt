package com.dmdbrands.gurus.weight.data.storage.db.entity.device

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.dmdbrands.gurus.weight.data.storage.db.converter.JsonConverter

/**
 * Entity class representing R4 scale preferences in the database.
 * Extends BodyScaleEntity through a one-to-one relationship.
 */
@Entity(
  tableName = "r4_scale_preference",
  foreignKeys = [
    ForeignKey(
      entity = DeviceEntity::class,
      parentColumns = ["id"],
      childColumns = ["id"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
)
@TypeConverters(JsonConverter::class)
data class R4ScalePreferenceEntity(
  @PrimaryKey
  val id: String,
  val displayName: String?,
  val displayMetrics: List<String>?,
  val shouldFactoryReset: Boolean = false,
  val shouldMeasureImpedance: Boolean = false,
  val shouldMeasurePulse: Boolean = false,
  val timeFormat: String?,
  val tzOffset: Int?,
  val wifiFotaScheduleTime: Int?,
  val isSynced: Boolean = false,
)
