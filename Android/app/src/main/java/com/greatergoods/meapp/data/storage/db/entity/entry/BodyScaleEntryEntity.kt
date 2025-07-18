package com.greatergoods.meapp.data.storage.db.entity.entry

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.PrimaryKey

/**
 * Entity class representing a body scale entry in the database.
 * Maps to the 'body_scale_entry' table in the SQLite database.
 */
@Entity(
  tableName = "body_scale_entry",
  foreignKeys = [
    ForeignKey(
      entity = EntryEntity::class,
      parentColumns = ["id"],
      childColumns = ["id"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
)
data class BodyScaleEntryEntity(
  @PrimaryKey
  val id: Long,
  val weight: Double,
  val bodyFat: Double?,
  val muscleMass: Double?,
  val water: Double?,
  val bmi: Double?,
  val source: String?
) {
  @Ignore
  var prefix: String? = null

  constructor(
    id: Long,
    weight: Double,
    bodyFat: Double?,
    muscleMass: Double?,
    water: Double?,
    bmi: Double?,
    source: String?,
    prefix: String?
  ) : this(id, weight, bodyFat, muscleMass, water, bmi, source) {
    this.prefix = prefix
  }
}
