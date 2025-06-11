package com.greatergoods.meapp.data.storage.db.entity.entry

import androidx.room.DatabaseView

/**
 * Database view representing valid (non-deleted) entries.
 */
@DatabaseView(
    viewName = "entry_view",
    value = """
        SELECT * FROM entry e
        WHERE e.operationType != 'delete'
          AND NOT EXISTS (
            SELECT 1 FROM entry d
            WHERE d.accountId = e.accountId
              AND d.entryTimestamp = e.entryTimestamp
              AND d.operationType = 'delete'
          )
    """,
)
data class ActiveEntryEntity(
    override val id: Long,
    override val accountId: String,
    override val entryTimestamp: String,
    override val serverTimestamp: String?,
    override val opTimestamp: String?,
    override val operationType: String,
    override val deviceType: String,
    override val deviceId: String,
    override val attempts: Int = 0,
    override val isSynced: Boolean = false,
) : BaseEntryEntity
