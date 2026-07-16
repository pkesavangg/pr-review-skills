package com.dmdbrands.gurus.weight.data.storage.db.entity.entry

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.AccountEntity
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit

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
            onDelete = ForeignKey.CASCADE
        ),
    ],
    indices = [
        Index("accountId"),
        Index("entryTimestamp"),
        Index(value = ["accountId", "entryTimestamp"], unique = true),
        Index(value = ["accountId", "operationType"]),
    ],
)
data class EntryEntity(
    @PrimaryKey(autoGenerate = true)
    override val id: Long = 0L,
    override val accountId: String,
    override val entryTimestamp: String,
    override val serverTimestamp: String? = null,
    override val opTimestamp: String? = null,
    override val operationType: String,
    override val deviceType: String,
    override val deviceId: String,
    override val attempts: Int = 0,
    override val unit: WeightUnit = WeightUnit.LB,
    override val isSynced: Boolean = false,
    /**
     * True while the row is in the swipe-delete Undo window: hidden from all reads (via
     * `entry_view`) but not yet actually deleted. Committed to a real delete when the window
     * elapses or on next app launch; cleared back to false on Undo. (MOB-1173 delete)
     */
    val pendingDelete: Boolean = false,
) : BaseEntryEntity

interface BaseEntryEntity {
    val id: Long
    val accountId: String
    val entryTimestamp: String
    val serverTimestamp: String?
    val opTimestamp: String?
    val operationType: String
    val deviceType: String
    val deviceId: String
    val attempts: Int
    val unit: WeightUnit
    val isSynced: Boolean
}
