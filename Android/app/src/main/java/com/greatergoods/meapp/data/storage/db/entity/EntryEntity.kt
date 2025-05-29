package com.greatergoods.meapp.data.storage.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
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
            parentColumns = ["account_id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("accountId")]
)
data class EntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long,

    @ColumnInfo(name = "accountId")
    val accountId: String,

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
    val deviceId: String,

    @ColumnInfo(name = "isSynced")
    val isSynced: Boolean = false
)