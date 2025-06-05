package com.greatergoods.meapp.data.storage.db.entity

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
            parentColumns = ["accountId"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("accountId")],
)
data class EntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    val accountId: String,
    val entryTimestamp: String,
    val serverTimestamp: String?,
    val opTimestamp: String?,
    val operationType: String,
    val deviceType: String,
    val deviceId: String,
    val isSynced: Boolean = false,
)
