package com.greatergoods.meapp.data.storage.db.entity.entry

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Entity class representing body scale entry metrics in the database.
 * Maps to the 'body_scale_entry_metric' table in the SQLite database.
 */
@Entity(
    tableName = "body_scale_entry_metric",
    foreignKeys = [
        ForeignKey(
            entity = BodyScaleEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BodyScaleEntryMetricEntity(
    @PrimaryKey
    val id: Long,
    val bmr: Double,
    val metabolicAge: Int?,
    val proteinPercent: Double?,
    val pulse: Int?,
    val skeletalMusclePercent: Double?,
    val subcutaneousFatPercent: Double?,
    val visceralFatLevel: Double,
    val boneMass: Double?,
    val impedance: Int?,
)

