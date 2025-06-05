package com.greatergoods.meapp.data.storage.db.entity

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
data class EntryViewEntity(
    val id: Long,
    val accountId: String,
    val entryTimestamp: String,
    val serverTimestamp: String?,
    val opTimestamp: String?,
    val operationType: String,
    val deviceType: String,
    val deviceId: String,
    val attempts: Int = 0,
    val isSynced: Boolean = false,
)
