package com.greatergoods.meapp.data.storage.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.greatergoods.meapp.data.storage.db.converter.JsonConverter

/**
 * Entity class representing R4 scale preferences in the database.
 * Maps to the 'r4_scale_preference' table in the SQLite database.
 */
@Entity(
    tableName = "r4_scale_preference",
    foreignKeys = [
        ForeignKey(
            entity = ScaleEntity::class,
            parentColumns = ["id"],
            childColumns = ["id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
@TypeConverters(JsonConverter::class)
data class R4ScalePreferenceEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "displayName")
    val displayName: String?,

    @ColumnInfo(name = "displayMetrics")
    val displayMetrics: List<String>?,

    @ColumnInfo(name = "shouldFactoryReset")
    val shouldFactoryReset: Boolean = false,

    @ColumnInfo(name = "shouldMeasureImpedance")
    val shouldMeasureImpedance: Boolean = false,

    @ColumnInfo(name = "shouldMeasurePulse")
    val shouldMeasurePulse: Boolean = false,

    @ColumnInfo(name = "timeFormat")
    val timeFormat: String?,

    @ColumnInfo(name = "tzOffset")
    val tzOffset: Int?,

    @ColumnInfo(name = "wifiFotaScheduleTime")
    val wifiFotaScheduleTime: Int?,

    @ColumnInfo(name = "updatedAt")
    val updatedAt: String?,
) : BaseEntity() 