package com.greatergoods.meapp.data.storage.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Entity class representing an entry in the database.
 * Maps to the 'entry' table in the SQLite database.
 */
@Entity(
    tableName = "entry",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class EntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long,

    @ColumnInfo(name = "userId")
    val userId: String,

    @ColumnInfo(name = "entryTimestamp")
    val entryTimestamp: String,

    @ColumnInfo(name = "serverTimestamp")
    val serverTimestamp: String?,

    @ColumnInfo(name = "opTimestamp")
    val opTimestamp: String?,

    @ColumnInfo(name = "operationType")
    val operationType: String,

    @ColumnInfo(name = "deviceType")
    val deviceType: String,

    @ColumnInfo(name = "deviceId")
    val deviceId: String
) : BaseEntity() 