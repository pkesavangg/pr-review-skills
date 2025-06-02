package com.greatergoods.meapp.data.storage.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Entity class representing scale entry metrics in the database.
 * Maps to the 'scale_entry_metric' table in the SQLite database.
 */
@Entity(
    tableName = "scale_entry_metric",
    foreignKeys = [
        ForeignKey(
            entity = WeightScaleEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ScaleEntryMetricEntity(
    @PrimaryKey
    val id: Long,

    val bmr: Int,

    val metabolicAge: Int,

    val proteinPercent: Int,

    val pulse: Int,

    val skeletalMusclePercent: Int,

    val subcutaneousFatPercent: Int,

    val visceralFatLevel: Int,

    val boneMass: Int,

    val impedance: Int,

    val unit: String
)