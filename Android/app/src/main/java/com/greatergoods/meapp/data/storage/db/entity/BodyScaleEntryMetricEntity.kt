package com.greatergoods.meapp.data.storage.db.entity

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
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class BodyScaleEntryMetricEntity(
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
