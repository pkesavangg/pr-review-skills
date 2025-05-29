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
            entity = ScaleEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ScaleEntryMetricEntity(
    @PrimaryKey
    val id: Long,

    @ColumnInfo(name = "bmr")
    val bmr: Int,

    @ColumnInfo(name = "metabolicAge")
    val metabolicAge: Int,

    @ColumnInfo(name = "proteinPercent")
    val proteinPercent: Int,

    @ColumnInfo(name = "pulse")
    val pulse: Int,

    @ColumnInfo(name = "skeletalMusclePercent")
    val skeletalMusclePercent: Int,

    @ColumnInfo(name = "subcutaneousFatPercent")
    val subcutaneousFatPercent: Int,

    @ColumnInfo(name = "visceralFatLevel")
    val visceralFatLevel: Int,

    @ColumnInfo(name = "boneMass")
    val boneMass: Int,

    @ColumnInfo(name = "impedance")
    val impedance: Int,

    @ColumnInfo(name = "unit")
    val unit: String
)