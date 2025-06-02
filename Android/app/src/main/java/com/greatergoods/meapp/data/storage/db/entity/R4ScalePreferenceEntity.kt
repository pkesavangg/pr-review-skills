package com.greatergoods.meapp.data.storage.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.greatergoods.meapp.data.storage.db.converter.JsonConverter

/**
 * Entity class representing R4 scale preferences in the database.
 * Extends BodyScaleEntity through a one-to-one relationship.
 */
@Entity(
    tableName = "r4_scale_preference",
    foreignKeys = [
        ForeignKey(
            entity = BodyScaleEntity::class,
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
    val updatedAt: String?,
)
