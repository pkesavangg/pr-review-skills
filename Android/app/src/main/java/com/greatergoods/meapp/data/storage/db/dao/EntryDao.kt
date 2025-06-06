package com.greatergoods.meapp.data.storage.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.greatergoods.meapp.data.storage.db.entity.BodyScaleEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.BodyScaleEntryMetricEntity
import com.greatergoods.meapp.data.storage.db.entity.BpmEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.Entry
import com.greatergoods.meapp.data.storage.db.entity.EntryEntity
import com.greatergoods.meapp.data.storage.db.entity.EntryView
import com.greatergoods.meapp.domain.model.common.HistoryMonth
import kotlinx.coroutines.flow.Flow
import java.util.Map.entry
import android.util.Log

/**
 * Data Access Object (DAO) for the entry table.
 * Provides methods to interact with the entry data in the database.
 * Includes methods for basic CRUD operations and specialized queries for different types of entries.
 */
@Dao
interface EntryDao {
    /**
     * Insert an Entry with related BpmEntry and BodyScaleEntry in a single transaction.
     * @param entry The complete entry data to insert
     * @return The row ID of the inserted EntryEntity
     */
    @Transaction
    suspend fun insert(entry: Entry): Long {
        val entryId = insertEntryEntity(entry.entry)
        Log.i("CHECKING", "EntryDao inserted entry with ID: $entryId")

        entry.bpmEntry?.let {
            insertBpm(it.copy(id = entryId))
        }

        entry.scaleEntry?.let {
            insertBodyScale(it.copy(id = entryId))
        }

        entry.scaleEntryMetric?.let {
            insertBodyScaleMetric(it.copy(id = entryId))
        }

        return entryId
    }

    /**
     * Insert a list of entries with their related details in a single transaction.
     * @param entries The list of Entry objects to insert
     */
    @Transaction
    suspend fun insert(entries: List<Entry>) {
        // Step 1: Insert all EntryEntity items and get their generated IDs
        val entryEntities = entries.map { it.entry }
        val entryIds = insertEntryEntity(entryEntities)

        // Step 2: Insert related BpmEntryEntity
        val bpmEntries = entries.mapIndexedNotNull { index, entry ->
            entry.bpmEntry?.copy(id = entryIds[index])
        }
        if (bpmEntries.isNotEmpty()) insertBpm(bpmEntries)

        // Step 3: Insert related BodyScaleEntryEntity
        val scaleEntries = entries.mapIndexedNotNull { index, entry ->
            entry.scaleEntry?.copy(id = entryIds[index])
        }
        if (scaleEntries.isNotEmpty()) insertBodyScale(scaleEntries)

        // Step 4: Insert related BodyScaleEntryMetricEntity
        val metricEntries = entries.mapIndexedNotNull { index, entry ->
            entry.scaleEntryMetric?.copy(id = entryIds[index])
        }
        if (metricEntries.isNotEmpty()) insertBodyScaleMetric(metricEntries)
    }

    @Transaction
    suspend fun update(entry: Entry) {
        update(entry.entry)
        entry.bpmEntry?.let {
            updateBpm(it)
        }
        entry.scaleEntry?.let {
            updateBodyScale(it)
        }
        entry.scaleEntryMetric?.let {
            updateBodyScaleMetric(it)
        }
    }

    /**
     * Marks an entry as deleted if it is not already marked as deleted.
     * Inserts a new delete operation entry with the current timestamp if needed.
     * @param entry The entry to mark as deleted.
     */
    @Transaction
    suspend fun delete(entry: Entry) {
        val timestamp = System.currentTimeMillis().toString()
        val deleteEntry = entry.entry.copy(
            id = 0,
            operationType = "DELETE",
            opTimestamp = timestamp,
        )
        insertEntryEntity(deleteEntry)
    }

    /**
     * Deletes an entry by its ID.
     * @param id The ID of the entry to delete.
     */
    @Transaction
    @Query("DELETE FROM entry WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    // Get Methods
    /**
     * Get the latest entry for a specific account with all related details.
     * @param accountId The account ID
     * @return The latest Entry with relations if found, null otherwise
     */
    @Transaction
    @Query("SELECT * FROM entry_view WHERE accountId = :accountId ORDER BY entryTimestamp DESC LIMIT 1")
    fun getLatestEntry(accountId: String): Flow<EntryView>?

    /**
     * Get all entries with their related details for a specific account.
     * This method uses @Transaction to ensure all related data is fetched atomically.
     * @param accountId The account ID
     * @return List of Entry containing entries and their related data for the account
     */
    @Transaction
    @Query("SELECT * FROM entry_view WHERE accountId = :accountId")
    suspend fun getEntriesByAccount(accountId: String): List<EntryView>

    /**
     * Get entries within a time range for a specific account with all related details.
     * @param accountId The account ID
     * @param startTime The start timestamp
     * @param endTime The end timestamp
     * @return A Flow of entries within the time range with relations
     */
    @Transaction
    @Query("SELECT * FROM entry_view WHERE accountId = :accountId AND entryTimestamp BETWEEN :startTime AND :endTime")
    fun getEntriesByTimeRange(
        accountId: String,
        startTime: String,
        endTime: String,
    ): Flow<List<EntryView>>

    /**
     * Get entries by device type for a specific account with all related details.
     * @param accountId The account ID
     * @param deviceType The device type
     * @return A Flow of entries for the specified device type with relations
     */
    @Transaction
    @Query("SELECT * FROM entry_view WHERE accountId = :accountId AND deviceType = :deviceType")
    fun getEntriesByDeviceType(
        accountId: String,
        deviceType: String,
    ): Flow<List<EntryView>>

    /**
     * Get an entry by its ID with all related details.
     * @param id The entry ID
     * @return The Entry with relations if found, null otherwise
     */
    @Transaction
    @Query("SELECT * FROM entry WHERE id = :id")
    suspend fun getEntryById(id: Long): Entry?

    /**
     * Get entries by operation type for a specific account.
     * @param accountId The account ID
     * @param operationType The operation type
     * @return A Flow of entries with the specified operation type
     */
    @Transaction
    @Query("SELECT * FROM entry WHERE accountId = :accountId AND operationType = :operationType")
    fun getEntriesByOperationType(
        accountId: String,
        operationType: String,
    ): Flow<List<Entry>>

    // UnSynced Operations

    /**
     * Get all operations in the UnSynced for an account.
     * @param accountId The account ID
     * @return List of EntryEntity objects in the UnSynced
     */
    @Transaction
    @Query("SELECT * FROM entry WHERE accountId = :accountId AND isSynced = 0 ORDER BY entryTimestamp ASC")
    suspend fun getUnSynced(accountId: String): List<Entry>

    /**
     * Update the attempts count for an operation in the UnSynced.
     * @param entry The EntryEntity to update attempts for
     * @return The number of rows updated
     */
    @Query("UPDATE entry SET attempts = attempts + 1 WHERE id = :id")
    suspend fun incrementAttempts(id: Long): Int

    /**
     * Get failed operations in the UnSynced for an account.
     * @param accountId The account ID
     * @param maxAttempts The maximum number of attempts to consider an operation as failed
     * @return List of EntryEntity objects that have failed operations
     */
    @Transaction
    @Query("SELECT * FROM entry WHERE accountId = :accountId AND attempts >= :maxAttempts AND isSynced = 0")
    suspend fun getFailedOperations(accountId: String, maxAttempts: Int): List<Entry>

    /**
     * Clear the UnSynced for an account.
     * @param accountId The account ID
     * @return The number of rows deleted
     */
    @Query("DELETE FROM entry WHERE accountId = :accountId AND isSynced = 0")
    suspend fun clearUnSynced(accountId: String): Int

    /**
     * Delete all entries for an account.
     * @param accountId The account ID
     * @return The number of rows deleted
     */
    @Query("DELETE FROM entry WHERE accountId = :accountId")
    suspend fun deleteAllEntriesForAccount(accountId: String): Int

    // Update methods for EntryEntity
    /**
     * Insert a new entry into the database.
     * @param entry The entry entity to insert
     * @return The row ID of the inserted entry
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntryEntity(entry: EntryEntity): Long

    /**
     * Insert a list of entry entities into the database.
     * @param entries The list of entry entities to insert
     * @return List of row IDs of the inserted entries
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntryEntity(entries: List<EntryEntity>): List<Long>

    /**
     * Insert a new BPM entity into the database.
     * @param bpm The BpmEntity to insert.
     * @return The row ID of the inserted entity.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBpm(bpm: BpmEntryEntity): Long

    /**
     * Insert a list of BPM entities into the database.
     * @param bpm The list of BpmEntryEntity to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBpm(bpm: List<BpmEntryEntity>)

    /**
     * Insert a new BodyScaleEntry entity into the database.
     * @param scale The BodyScaleEntryEntity to insert.
     * @return The row ID of the inserted entity.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBodyScale(scale: BodyScaleEntryEntity): Long

    /**
     * Insert a list of BodyScaleEntry entities into the database.
     * @param scales The list of BodyScaleEntryEntity to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBodyScale(scales: List<BodyScaleEntryEntity>)

    /**
     * Insert a new BodyScaleEntryMetric entity into the database.
     * @param metric The BodyScaleEntryMetricEntity to insert.
     * @return The row ID of the inserted entity.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBodyScaleMetric(metric: BodyScaleEntryMetricEntity): Long

    /**
     * Insert a list of BodyScaleEntryMetric entities into the database.
     * @param metrics The list of BodyScaleEntryMetricEntity to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBodyScaleMetric(metrics: List<BodyScaleEntryMetricEntity>)

    // Update methods for EntryEntity
    /**
     * Update an existing entry in the database.
     * @param entry The entry entity to update
     * @return The number of rows updated
     */
    @Update
    suspend fun update(entry: EntryEntity): Int

    /**
     * Update an existing BPM entity in the database.
     * @param bpm The BpmEntity to update.
     * @return The number of rows updated.
     */
    @Update
    suspend fun updateBpm(bpm: BpmEntryEntity): Int

    /**
     * Update an existing BodyScaleEntry entity in the database.
     * @param scale The BodyScaleEntryEntity to update.
     * @return The number of rows updated.
     */
    @Update
    suspend fun updateBodyScale(scale: BodyScaleEntryEntity): Int

    /**
     * Update an existing BodyScaleEntryMetric entity in the database.
     * @param metric The BodyScaleEntryMetricEntity to update.
     * @return The number of rows updated.
     */
    @Update
    suspend fun updateBodyScaleMetric(metric: BodyScaleEntryMetricEntity): Int

    /**
     * Get entries for a specific month and year.
     * @param accountId The account ID
     * @param month The month in YYYY-MM format
     * @return Flow of list of entries for the specified month
     */
    @Transaction
    @Query(
        """
        SELECT * FROM entry_view
        WHERE accountId = :accountId
        AND strftime('%Y-%m', datetime(entryTimestamp/1000, 'unixepoch')) = :month
        ORDER BY entryTimestamp DESC
    """,
    )
    fun getMonthDetail(accountId: String, month: String): Flow<List<EntryView>>

    /**
     * Get monthly aggregated data for the last year.
     * @param accountId The account ID
     * @return Flow of list of monthly aggregated data
     */
    @Transaction
    @Query(
        """
        SELECT
            MIN(entry_view.id) as id,
            ROUND(AVG(body_scale_entry.weight)) as weight,
            COUNT(DISTINCT entry_view.entryTimestamp) as count,
            GROUP_CONCAT(body_scale_entry.weight || '|' || entry_view.entryTimestamp) as weights,
            strftime('%Y-%m', datetime(entry_view.entryTimestamp/1000, 'unixepoch')) as entryTimestamp
        FROM entry_view
        INNER JOIN body_scale_entry ON entry_view.id = body_scale_entry.id
        WHERE entry_view.accountId = :accountId
        AND entry_view.entryTimestamp >= strftime('%s', 'now', '-1 year') * 1000
        AND entry_view.entryTimestamp <= strftime('%s', 'now') * 1000
        GROUP BY strftime('%Y-%m', datetime(entry_view.entryTimestamp/1000, 'unixepoch'))
        ORDER BY entryTimestamp DESC
    """,
    )
    fun getMonthsLastYear(accountId: String): Flow<List<HistoryMonth>>

    /**
     * Get all monthly aggregated data.
     * @param accountId The account ID
     * @return Flow of list of all monthly aggregated data
     */
    @Transaction
    @Query(
        """
        SELECT
            MIN(entry_view.id) as id,
            ROUND(AVG(body_scale_entry.weight)) as weight,
            COUNT(DISTINCT entry_view.entryTimestamp) as count,
            GROUP_CONCAT(body_scale_entry.weight || '|' || entry_view.entryTimestamp) as weights,
            strftime('%Y-%m', datetime(entry_view.entryTimestamp/1000, 'unixepoch')) as entryTimestamp
        FROM entry_view
        INNER JOIN body_scale_entry ON entry_view.id = body_scale_entry.id
        WHERE entry_view.accountId = :accountId
        GROUP BY strftime('%Y-%m', datetime(entry_view.entryTimestamp/1000, 'unixepoch'))
        ORDER BY entryTimestamp DESC
    """,
    )
    fun getMonthsAll(accountId: String): Flow<List<HistoryMonth>>

    /**
     * Get the operation count for an account.
     * @param accountId The account ID
     * @return The number of operations
     */
    @Query("SELECT COUNT(*) FROM entry WHERE accountId = :accountId")
    suspend fun getOperationCount(accountId: String): Int
}



