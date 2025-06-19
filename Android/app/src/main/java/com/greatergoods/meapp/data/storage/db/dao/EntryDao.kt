package com.greatergoods.meapp.data.storage.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryMetricEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.BpmEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.EntryEntity
import com.greatergoods.meapp.domain.model.common.HistoryMonth
import com.greatergoods.meapp.domain.model.storage.entry.BpmEntry
import com.greatergoods.meapp.domain.model.storage.entry.Entry
import com.greatergoods.meapp.domain.model.storage.entry.PeriodBodyScaleSummary
import com.greatergoods.meapp.domain.model.storage.entry.PopulatedActiveEntry
import com.greatergoods.meapp.domain.model.storage.entry.PopulatedEntry
import com.greatergoods.meapp.domain.model.storage.entry.ScaleEntry
import kotlinx.coroutines.flow.Flow
import java.util.Map.entry

/**
 * Data Access Object (DAO) for the entry table.
 * Provides methods to interact with the entry data in the database.
 * Includes methods for basic CRUD operations and specialized queries for different types of entries.
 * All datetime values are stored and handled in ISO 8601 format (e.g. "2025-06-19T06:30:00.000Z")
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

        if (entry is BpmEntry) insertBpm(entry.bpmEntry.copy(id = entryId))
        else if (entry is ScaleEntry) {
            insertBodyScale(entry.scale.scaleEntry.copy(id = entryId))
            if (entry.scale.scaleEntryMetric != null) {
                insertBodyScaleMetric(entry.scale.scaleEntryMetric.copy(id = entryId))
            }
        }
        return entryId
    }

    /**
     * Insert a list of entries with their related details in a single transaction.
     * @param entries The list of Entry objects to insert
     */
    @Transaction
    suspend fun insert(entries: List<Entry>) {
        entries.forEach {
            insert(it)
        }
    }

    @Transaction
    suspend fun update(entry: Entry): Long {
        val updatedId = update(entry.entry).toLong()

        if (entry is BpmEntry) {
            updateBpm(entry.bpmEntry.copy(id = updatedId))
        } else if (entry is ScaleEntry) {
            updateBodyScale(entry.scale.scaleEntry.copy(id = updatedId))
            if (entry.scale.scaleEntryMetric != null) {
                updateBodyScaleMetric(entry.scale.scaleEntryMetric.copy(id = updatedId))
            }
        }
        return updatedId
    }

    /**
     * Marks an entry as deleted if it is not already marked as deleted.
     * Inserts a new delete operation entry with the current timestamp if needed.
     * @param entry The entry to mark as deleted.
     */
    @Transaction
    suspend fun delete(entry: Entry) {
        val deleteEntry = entry.entry.copy(
            id = 0,
            operationType = "DELETE",
            opTimestamp = entry.entry.opTimestamp,
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
    @Query("SELECT * FROM entry_view WHERE accountId = :accountId ORDER BY datetime(entryTimestamp) DESC LIMIT 1")
    fun getLatestEntry(accountId: String): Flow<PopulatedActiveEntry>?

    /**
     * Get all entries with their related details for a specific account.
     * This method uses @Transaction to ensure all related data is fetched atomically.
     * @param accountId The account ID
     * @return List of Entry containing entries and their related data for the account
     */
    @Transaction
    @Query("SELECT * FROM entry_view WHERE accountId = :accountId")
    suspend fun getEntriesByAccount(accountId: String): List<PopulatedActiveEntry>

    /**
     * Get entries within a time range for a specific account.
     * @param accountId The account ID
     * @param startTime The start time in ISO 8601 format
     * @param endTime The end time in ISO 8601 format
     * @return A Flow of entries within the time range with relations
     */
    @Transaction
    @Query(
        """
        SELECT *
        FROM entry_view
        WHERE accountId = :accountId
          AND datetime(entryTimestamp) BETWEEN datetime(:startTime) AND datetime(:endTime)
          AND entryTimestamp IN (
            SELECT MAX(entryTimestamp)
            FROM entry_view
            WHERE accountId = :accountId
              AND datetime(entryTimestamp) BETWEEN datetime(:startTime) AND datetime(:endTime)
            GROUP BY strftime('%Y-%m-%d', datetime(entryTimestamp))
          )
        ORDER BY datetime(entryTimestamp) DESC
    """,
    )
    fun getEntriesByTimeRange(
        accountId: String,
        startTime: String,
        endTime: String,
    ): Flow<List<PopulatedActiveEntry>>

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
    ): Flow<List<PopulatedActiveEntry>>

    /**
     * Get an entry by its ID with all related details.
     * @param id The entry ID
     * @return The Entry with relations if found, null otherwise
     */
    @Transaction
    @Query("SELECT * FROM entry WHERE id = :id")
    suspend fun getEntryById(id: Long): PopulatedEntry?

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
    ): Flow<List<PopulatedEntry>>

    // UnSynced Operations

    /**
     * Get all operations in the UnSynced for an account.
     * @param accountId The account ID
     * @return List of EntryEntity objects in the UnSynced
     */
    @Transaction
    @Query("SELECT * FROM entry WHERE accountId = :accountId AND isSynced = 0 ORDER BY entryTimestamp ASC")
    suspend fun getUnSynced(accountId: String): List<PopulatedEntry>

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
    suspend fun getFailedOperations(accountId: String, maxAttempts: Int): List<PopulatedEntry>

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
     * Get entries for a specific month.
     * @param accountId The account ID
     * @param month The month in YYYY-MM format
     * @return Flow of list of entries for the specified month
     */
    @Transaction
    @Query(
        """
        SELECT * FROM entry_view
        WHERE accountId = :accountId
        AND strftime('%Y-%m', datetime(entryTimestamp)) = :month
        ORDER BY datetime(entryTimestamp) DESC
    """,
    )
    fun getMonthDetail(accountId: String, month: String): Flow<List<PopulatedActiveEntry>>

    /**
     * Get the operation count for an account.
     * @param accountId The account ID
     * @return The number of operations
     */
    @Query("SELECT COUNT(*) FROM entry WHERE accountId = :accountId")
    suspend fun getOperationCount(accountId: String): Int

    /**
     * Get metrics for a specific entry.
     * @param entryId The ID of the entry
     * @return Flow of BodyScaleEntryMetricEntity for the entry
     */
    @Query("SELECT * FROM body_scale_entry_metric WHERE id = :entryId")
    fun getMetricsByEntryId(entryId: Long): Flow<BodyScaleEntryMetricEntity>

    /**
     * Insert a list of scale entries into the database.
     * @param entries The list of BodyScaleEntryEntity objects to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScaleEntries(entries: List<BodyScaleEntryEntity>)

    /**
     * Get a scale entry by its ID.
     * @param entryId The ID of the entry
     * @return The BodyScaleEntryEntity if found, null otherwise
     */
    @Query("SELECT * FROM body_scale_entry WHERE id = :entryId")
    suspend fun getScaleEntryById(entryId: Long): BodyScaleEntryEntity?

    /**
     * Get monthly averages of body scale data for an account.
     *
     * This query joins the entry, body_scale_entry, and body_scale_entry_metric tables to aggregate
     * all relevant body scale and metric fields. If you need to reuse this join pattern elsewhere,
     * consider creating a database VIEW or a temporary table for maintainability and performance.
     */
    @Query(
        """
        SELECT
          strftime('%Y-%m', datetime(e.entryTimestamp)) AS period,
          MAX(e.entryTimestamp) AS entryTimestamp,
          AVG(bse.weight) AS weight,
          AVG(bse.bodyFat) AS bodyFat,
          AVG(bse.muscleMass) AS muscleMass,
          AVG(bse.water) AS water,
          AVG(bse.bmi) AS bmi,
          AVG(bsem.bmr) AS bmr,
          AVG(bsem.metabolicAge) AS metabolicAge,
          AVG(bsem.proteinPercent) AS proteinPercent,
          AVG(bsem.pulse) AS pulse,
          AVG(bsem.skeletalMusclePercent) AS skeletalMusclePercent,
          AVG(bsem.subcutaneousFatPercent) AS subcutaneousFatPercent,
          AVG(bsem.visceralFatLevel) AS visceralFatLevel,
          AVG(bsem.boneMass) AS boneMass,
          AVG(bsem.impedance) AS impedance,
          MAX(e.unit) AS unit
        FROM entry AS e
        LEFT JOIN body_scale_entry AS bse ON e.id = bse.id
        LEFT JOIN body_scale_entry_metric AS bsem ON e.id = bsem.id
        WHERE e.accountId = :accountId
        GROUP BY period
        ORDER BY period DESC
    """,
    )
    fun getMonthlyBodyScaleAveragesWithJoin(
        accountId: String
    ): Flow<List<PeriodBodyScaleSummary>>

    /**
     * Get the latest body scale entry for each month for an account.
     *
     * This query joins the entry, body_scale_entry, and body_scale_entry_metric tables to fetch
     * all relevant body scale and metric fields for the latest entry in each month. For repeated
     * use, consider creating a VIEW or temporary table.
     */
    @Query(
        """
        SELECT
          strftime('%Y-%m', datetime(e.entryTimestamp)) AS period,
          e.entryTimestamp,
          bse.weight,
          bse.bodyFat,
          bse.muscleMass,
          bse.water,
          bse.bmi,
          bsem.bmr,
          bsem.metabolicAge,
          bsem.proteinPercent,
          bsem.pulse,
          bsem.skeletalMusclePercent,
          bsem.subcutaneousFatPercent,
          bsem.visceralFatLevel,
          bsem.boneMass,
          bsem.impedance,
          e.unit
        FROM entry AS e
        LEFT JOIN body_scale_entry AS bse ON e.id = bse.id
        LEFT JOIN body_scale_entry_metric AS bsem ON e.id = bsem.id
        WHERE e.accountId = :accountId
          AND e.entryTimestamp IN (
            SELECT MAX(entryTimestamp)
            FROM entry
            WHERE accountId = :accountId
            GROUP BY strftime('%Y-%m', datetime(entryTimestamp))
          )
        ORDER BY period DESC
    """,
    )
    fun getMonthlyBodyScaleLatestWithJoin(
        accountId: String
    ): Flow<List<PeriodBodyScaleSummary>>

    /**
     * Get daywise averages of body scale data for an account.
     *
     * This query joins the entry, body_scale_entry, and body_scale_entry_metric tables to aggregate
     * all relevant body scale and metric fields by day. For repeated use, consider a VIEW or temp table.
     */
    @Query(
        """
        SELECT
          strftime('%Y-%m-%d', datetime(e.entryTimestamp)) AS period,
          MAX(e.entryTimestamp) AS entryTimestamp,
          AVG(bse.weight) AS weight,
          AVG(bse.bodyFat) AS bodyFat,
          AVG(bse.muscleMass) AS muscleMass,
          AVG(bse.water) AS water,
          AVG(bse.bmi) AS bmi,
          AVG(bsem.bmr) AS bmr,
          AVG(bsem.metabolicAge) AS metabolicAge,
          AVG(bsem.proteinPercent) AS proteinPercent,
          AVG(bsem.pulse) AS pulse,
          AVG(bsem.skeletalMusclePercent) AS skeletalMusclePercent,
          AVG(bsem.subcutaneousFatPercent) AS subcutaneousFatPercent,
          AVG(bsem.visceralFatLevel) AS visceralFatLevel,
          AVG(bsem.boneMass) AS boneMass,
          AVG(bsem.impedance) AS impedance,
          MAX(e.unit) AS unit
        FROM entry AS e
        LEFT JOIN body_scale_entry AS bse ON e.id = bse.id
        LEFT JOIN body_scale_entry_metric AS bsem ON e.id = bsem.id
        WHERE e.accountId = :accountId
        GROUP BY period
        ORDER BY period DESC
    """,
    )
    fun getDaywiseBodyScaleAveragesWithJoin(
        accountId: String
    ): Flow<List<PeriodBodyScaleSummary>>

    /**
     * Get the latest body scale entry for each day for an account.
     *
     * This query joins the entry, body_scale_entry, and body_scale_entry_metric tables to fetch
     * all relevant body scale and metric fields for the latest entry in each day. For repeated
     * use, consider a VIEW or temp table.
     */
    @Query(
        """
        SELECT
          strftime('%Y-%m-%d', datetime(e.entryTimestamp)) AS period,
          e.entryTimestamp,
          bse.weight,
          bse.bodyFat,
          bse.muscleMass,
          bse.water,
          bse.bmi,
          bsem.bmr,
          bsem.metabolicAge,
          bsem.proteinPercent,
          bsem.pulse,
          bsem.skeletalMusclePercent,
          bsem.subcutaneousFatPercent,
          bsem.visceralFatLevel,
          bsem.boneMass,
          bsem.impedance,
          e.unit
        FROM entry AS e
        LEFT JOIN body_scale_entry AS bse ON e.id = bse.id
        LEFT JOIN body_scale_entry_metric AS bsem ON e.id = bsem.id
        WHERE e.accountId = :accountId
          AND e.entryTimestamp IN (
            SELECT MAX(entryTimestamp)
            FROM entry
            WHERE accountId = :accountId
            GROUP BY strftime('%Y-%m-%d', datetime(entryTimestamp))
          )
        ORDER BY period DESC
    """,
    )
    fun getDaywiseBodyScaleLatestWithJoin(
        accountId: String
    ): Flow<List<PeriodBodyScaleSummary>>

    @Query(
        """
    WITH entries_with_period AS (
        SELECT
            e.entryTimestamp,
            bse.weight,
            strftime('%Y-%m', datetime(e.entryTimestamp)) AS period
        FROM entry_view e
        LEFT JOIN body_scale_entry bse ON e.id = bse.id
        WHERE e.accountId = :accountId AND bse.weight IS NOT NULL
    ),
    first_last AS (
        SELECT
            period,
            MIN(entryTimestamp) AS firstTimestamp,
            MAX(entryTimestamp) AS lastTimestamp
        FROM entries_with_period
        GROUP BY period
    ),
    joined AS (
        SELECT
            fl.period,
            fl.firstTimestamp,
            fl.lastTimestamp,
            (SELECT weight FROM entries_with_period WHERE entryTimestamp = fl.firstTimestamp) AS firstWeight,
            (SELECT weight FROM entries_with_period WHERE entryTimestamp = fl.lastTimestamp) AS lastWeight,
            (SELECT AVG(weight) FROM entries_with_period WHERE period = fl.period) AS avgWeight,
            (SELECT COUNT(*) FROM entries_with_period WHERE period = fl.period) AS entryCount
        FROM first_last fl
    )
    SELECT
        firstTimestamp AS entryTimestamp,
        avgWeight,
        entryCount,
        lastWeight - firstWeight AS change
    FROM joined
    ORDER BY period DESC
    """,
    )
    fun getMonthlyHistory(
        accountId: String
    ): Flow<List<HistoryMonth>>
}
