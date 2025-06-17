package com.greatergoods.meapp.data.storage.db.entity.entry

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.greatergoods.meapp.data.storage.db.entity.account.AccountEntity

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
        ),
    ],
    indices = [Index("accountId")],
)
data class EntryEntity(
    @PrimaryKey(autoGenerate = true)
    override val id: Long,
    override val accountId: String,
    override val entryTimestamp: Long,
    override val serverTimestamp: Long?,
    override val opTimestamp: Long?,
    override val operationType: String,
    override val deviceType: String,
    override val deviceId: String,
    override val attempts: Int = 0,
    override val isSynced: Boolean = false,
) : BaseEntryEntity

interface BaseEntryEntity {
    val id: Long
    val accountId: String
    val entryTimestamp: Long
    val serverTimestamp: Long?
    val opTimestamp: Long?
    val operationType: String
    val deviceType: String
    val deviceId: String
    val attempts: Int
    val isSynced: Boolean
}
