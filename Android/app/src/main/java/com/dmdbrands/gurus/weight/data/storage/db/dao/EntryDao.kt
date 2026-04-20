package com.dmdbrands.gurus.weight.data.storage.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryMetricEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BabyEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BpmEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity
import com.dmdbrands.gurus.weight.domain.model.common.HistoryMonth
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BabyEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BpmEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PopulatedActiveEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PopulatedEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for the entry table.
 * Provides methods to interact with the entry data in the database.
 * Includes methods for basic CRUD operations and specialized queries for different types of entries.
 * All datetime values are stored and handled in ISO 8601 format (e.g. "2025-06-19T06:30:00.000Z")
 */
@Dao
interface EntryDao {
  companion object {
    /**
     * SQLite datetime modifier constants.
     */
    const val UTC = "'utc'"
    const val LOCAL_TIME = "'localtime'"
    const val START_OF_DAY = "'start of day'"
    const val START_OF_MONTH = "'start of month'"

    /**
     * Month abbreviation constants for SQL CASE statements.
     */
    const val MONTH_JAN = "'Jan'"
    const val MONTH_FEB = "'Feb'"
    const val MONTH_MAR = "'Mar'"
    const val MONTH_APR = "'Apr'"
    const val MONTH_MAY = "'May'"
    const val MONTH_JUN = "'Jun'"
    const val MONTH_JUL = "'Jul'"
    const val MONTH_AUG = "'Aug'"
    const val MONTH_SEP = "'Sep'"
    const val MONTH_OCT = "'Oct'"
    const val MONTH_NOV = "'Nov'"
    const val MONTH_DEC = "'Dec'"
  }

  /**
   * Insert an Entry with related BpmEntry and BodyScaleEntry in a single transaction.
   * @param entry The complete entry data to insert
   * @return The row ID of the inserted EntryEntity
   */
  @Transaction
  suspend fun insert(entry: Entry): Long {
    val entryId = insertEntryEntity(entry.entry)

    if (entry is BpmEntry) insertBpm(entry.bpmEntry.copy(id =entryId))
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
   * Inserts one entry at a time so each [insertEntryEntity] returns the correct generated id
   * (SQLite multi-row INSERT returns only the last rowid, so bulk insert + returned IDs would break
   * the body_scale_entry foreign key to entry). One transaction still gives a large speedup vs N transactions.
   * @param entries The list of Entry objects to insert
   */
  @Transaction
  suspend fun insert(entries: List<Entry>) {
    if (entries.isEmpty()) return
    for (entry in entries) {
      val entryId = insertEntryEntity(entry.entry)
      when (entry) {
        is BpmEntry -> insertBpm(entry.bpmEntry.copy(id =entryId))
        is ScaleEntry -> {
          insertBodyScale(entry.scale.scaleEntry.copy(id = entryId))
          entry.scale.scaleEntryMetric?.let { insertBodyScaleMetric(it.copy(id = entryId)) }
        }
        is BabyEntry -> insertBabyEntry(entry.babyEntry.copy(id = entryId))
      }
    }
  }

  @Transaction
  suspend fun update(entry: Entry): Long {
    val updatedId = update(entry.entry).toLong()

    if (entry is BpmEntry) {
      updateBpm(entry.bpmEntry.copy(id =updatedId))
    } else if (entry is ScaleEntry) {
      updateBodyScale(entry.scale.scaleEntry.copy(id = updatedId))
      if (entry.scale.scaleEntryMetric != null) {
        updateBodyScaleMetric(entry.scale.scaleEntryMetric.copy(id = updatedId))
      }
    }
    return updatedId
  }

  /**
   * Marks an entry as deleted by updating the existing row to operationType = "delete".
   * The same row is kept so sync can send the delete to the server; display queries filter it out.
   */
  @Transaction
  suspend fun delete(entry: Entry) {
    update(entry.entry.copy(operationType = "delete"))
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
  @Query("SELECT * FROM entry_view WHERE accountId = :accountId AND (operationType IS NULL OR operationType != 'delete') ORDER BY datetime(entryTimestamp) DESC LIMIT 1")
  fun getLatestEntry(accountId: String): Flow<PopulatedActiveEntry?>

  /**
   * Get all entries with their related details for a specific account.
   * This method uses @Transaction to ensure all related data is fetched atomically.
   * @param accountId The account ID
   * @return List of Entry containing entries and their related data for the account
   */
  @Transaction
  @Query("SELECT * FROM entry_view WHERE accountId = :accountId AND (operationType IS NULL OR operationType != 'delete')")
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
          AND (operationType IS NULL OR operationType != 'delete')
          AND datetime(entryTimestamp) BETWEEN datetime(:startTime) AND datetime(:endTime)
          AND entryTimestamp IN (
            SELECT MAX(entryTimestamp)
            FROM entry_view
            WHERE accountId = :accountId
              AND (operationType IS NULL OR operationType != 'delete')
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
  @Query("SELECT * FROM entry_view WHERE accountId = :accountId AND deviceType = :deviceType AND (operationType IS NULL OR operationType != 'delete')")
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
  @Query("SELECT * FROM entry WHERE id = :id AND (operationType IS NULL OR operationType != 'delete')")
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
   * Insert a list of entry entities into the database (bulk).
   * Use distinct name from single-arg insertEntryEntity to avoid Kotlin overload resolution issues.
   * @param entries The list of entry entities to insert
   * @return List of row IDs of the inserted entries
   */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertEntries(entries: List<EntryEntity>): List<Long>

  /**
   * Insert a new BPM entity into the database.
   * @param bpm The BpmEntity to insert.
   * @return The row ID of the inserted entity.
   */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertBpm(bpm: BpmEntryEntity): Long

  /**
   * Insert a list of BPM entities into the database (bulk).
   * Use distinct name from single-arg insertBpm to avoid Kotlin overload resolution issues.
   * @param bpm The list of BpmEntryEntity to insert.
   */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertBpmList(bpm: List<BpmEntryEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertBabyEntry(babyEntry: BabyEntryEntity): Long

  /**
   * Insert a new BodyScaleEntry entity into the database.
   * @param scale The BodyScaleEntryEntity to insert.
   * @return The row ID of the inserted entity.
   */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertBodyScale(scale: BodyScaleEntryEntity): Long

  /**
   * Insert a list of BodyScaleEntry entities into the database (bulk).
   * Use distinct name from single-arg insertBodyScale to avoid Kotlin overload resolution issues.
   * @param scales The list of BodyScaleEntryEntity to insert.
   */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertBodyScales(scales: List<BodyScaleEntryEntity>)

  /**
   * Insert a new BodyScaleEntryMetric entity into the database.
   * @param metric The BodyScaleEntryMetricEntity to insert.
   * @return The row ID of the inserted entity.
   */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertBodyScaleMetric(metric: BodyScaleEntryMetricEntity): Long

  /**
   * Insert a list of BodyScaleEntryMetric entities into the database (bulk).
   * Use distinct name from single-arg insertBodyScaleMetric to avoid Kotlin overload resolution issues.
   * @param metrics The list of BodyScaleEntryMetricEntity to insert.
   */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertBodyScaleMetrics(metrics: List<BodyScaleEntryMetricEntity>)

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
        AND (operationType IS NULL OR operationType != 'delete')
        AND strftime('%Y-%m', datetime(entryTimestamp,${UTC}, ${LOCAL_TIME})) = :month
        ORDER BY datetime(entryTimestamp, ${UTC}, ${LOCAL_TIME}) DESC
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
          strftime('%Y-%m', datetime(e.entryTimestamp,${UTC}, ${LOCAL_TIME})) AS period,
          datetime(MIN(e.entryTimestamp),${UTC}, ${LOCAL_TIME},${START_OF_MONTH}) AS entryTimestamp,
          AVG(CASE WHEN bse.weight > 0 THEN bse.weight ELSE NULL END) AS weight,
          AVG(CASE WHEN bse.bodyFat > 0 THEN bse.bodyFat ELSE NULL END) AS bodyFat,
          AVG(CASE WHEN bse.muscleMass > 0 THEN bse.muscleMass ELSE NULL END) AS muscleMass,
          AVG(CASE WHEN bse.water > 0 THEN bse.water ELSE NULL END) AS water,
          AVG(CASE WHEN bse.bmi > 0 THEN bse.bmi ELSE NULL END) AS bmi,
          AVG(CASE WHEN bsem.bmr > 0 THEN bsem.bmr ELSE NULL END) AS bmr,
          AVG(CASE WHEN bsem.metabolicAge > 0 THEN bsem.metabolicAge ELSE NULL END) AS metabolicAge,
          AVG(CASE WHEN bsem.proteinPercent > 0 THEN bsem.proteinPercent ELSE NULL END) AS proteinPercent,
          AVG(CASE WHEN bsem.pulse > 0 THEN bsem.pulse ELSE NULL END) AS pulse,
          AVG(CASE WHEN bsem.skeletalMusclePercent > 0 THEN bsem.skeletalMusclePercent ELSE NULL END) AS skeletalMusclePercent,
          AVG(CASE WHEN bsem.subcutaneousFatPercent > 0 THEN bsem.subcutaneousFatPercent ELSE NULL END) AS subcutaneousFatPercent,
          AVG(CASE WHEN bsem.visceralFatLevel > 0 THEN bsem.visceralFatLevel ELSE NULL END) AS visceralFatLevel,
          AVG(CASE WHEN bsem.boneMass > 0 THEN bsem.boneMass ELSE NULL END) AS boneMass,
          AVG(CASE WHEN bsem.impedance > 0 THEN bsem.impedance ELSE NULL END) AS impedance,
          MAX(e.unit) AS unit
        FROM entry_view AS e
        LEFT JOIN body_scale_entry AS bse ON e.id = bse.id
        LEFT JOIN body_scale_entry_metric AS bsem ON e.id = bsem.id
        WHERE e.accountId = :accountId
          AND (e.operationType IS NULL OR e.operationType != 'delete')
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
          strftime('%Y-%m', datetime(e.entryTimestamp,${UTC}, ${LOCAL_TIME})) AS period,
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
        FROM entry_view AS e
        LEFT JOIN body_scale_entry AS bse ON e.id = bse.id
        LEFT JOIN body_scale_entry_metric AS bsem ON e.id = bsem.id
        WHERE e.accountId = :accountId
          AND (e.operationType IS NULL OR e.operationType != 'delete')
          AND e.entryTimestamp IN (
            SELECT MAX(entryTimestamp)
            FROM entry
            WHERE accountId = :accountId
              AND (operationType IS NULL OR operationType != 'delete')
            GROUP BY strftime('%Y-%m', datetime(entryTimestamp, ${UTC}, ${LOCAL_TIME}))
          )
        ORDER BY period DESC
    """,
  )
  fun getMonthlyBodyScaleLatestWithJoin(
    accountId: String
  ): Flow<List<PeriodBodyScaleSummary>>

  @Query(
    """
    SELECT
      strftime('%Y-%m-%d', datetime(e.entryTimestamp,${UTC}, ${LOCAL_TIME})) AS period,
      datetime(MIN(e.entryTimestamp),${UTC}, ${LOCAL_TIME},${START_OF_DAY}) AS entryTimestamp,
      AVG(CASE WHEN bse.weight > 0 THEN bse.weight ELSE NULL END) AS weight,
      AVG(CASE WHEN bse.bodyFat > 0 THEN bse.bodyFat ELSE NULL END) AS bodyFat,
      AVG(CASE WHEN bse.muscleMass > 0 THEN bse.muscleMass ELSE NULL END) AS muscleMass,
      AVG(CASE WHEN bse.water > 0 THEN bse.water ELSE NULL END) AS water,
      AVG(CASE WHEN bse.bmi > 0 THEN bse.bmi ELSE NULL END) AS bmi,
      AVG(CASE WHEN bsem.bmr > 0 THEN bsem.bmr ELSE NULL END) AS bmr,
      AVG(CASE WHEN bsem.metabolicAge > 0 THEN bsem.metabolicAge ELSE NULL END) AS metabolicAge,
      AVG(CASE WHEN bsem.proteinPercent > 0 THEN bsem.proteinPercent ELSE NULL END) AS proteinPercent,
      AVG(CASE WHEN bsem.pulse > 0 THEN bsem.pulse ELSE NULL END) AS pulse,
      AVG(CASE WHEN bsem.skeletalMusclePercent > 0 THEN bsem.skeletalMusclePercent ELSE NULL END) AS skeletalMusclePercent,
      AVG(CASE WHEN bsem.subcutaneousFatPercent > 0 THEN bsem.subcutaneousFatPercent ELSE NULL END) AS subcutaneousFatPercent,
      AVG(CASE WHEN bsem.visceralFatLevel > 0 THEN bsem.visceralFatLevel ELSE NULL END) AS visceralFatLevel,
      AVG(CASE WHEN bsem.boneMass > 0 THEN bsem.boneMass ELSE NULL END) AS boneMass,
      AVG(CASE WHEN bsem.impedance > 0 THEN bsem.impedance ELSE NULL END) AS impedance,
      MAX(e.unit) AS unit
    FROM entry_view AS e
    LEFT JOIN body_scale_entry AS bse ON e.id = bse.id
    LEFT JOIN body_scale_entry_metric AS bsem ON e.id = bsem.id
    WHERE e.accountId = :accountId
      AND (e.operationType IS NULL OR e.operationType != 'delete')
    GROUP BY strftime('%Y-%m-%d', datetime(e.entryTimestamp, ${UTC}, ${LOCAL_TIME}))
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
WITH daily_entries AS (
  SELECT
    strftime('%Y-%m-%d', datetime(e.entryTimestamp,${UTC}, ${LOCAL_TIME})) AS day,
    e.entryTimestamp,
    e.unit,
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
    bsem.impedance
  FROM entry_view e
  LEFT JOIN body_scale_entry bse ON e.id = bse.id
  LEFT JOIN body_scale_entry_metric bsem ON e.id = bsem.id
  WHERE e.accountId = :accountId
    AND (e.operationType IS NULL OR e.operationType != 'delete')
),
distinct_days AS (
  SELECT DISTINCT day FROM daily_entries
)
SELECT
  d.day AS period,
  -- Get the latest unit and entryTimestamp for that day
  (SELECT unit FROM daily_entries
   WHERE day = d.day AND unit IS NOT NULL
   ORDER BY entryTimestamp DESC LIMIT 1) AS unit,

  (SELECT entryTimestamp FROM daily_entries
   WHERE day = d.day
   ORDER BY entryTimestamp DESC LIMIT 1) AS entryTimestamp,

  -- For each metric, get the latest valid value for that day
  (SELECT weight FROM daily_entries
   WHERE day = d.day AND weight IS NOT NULL AND weight > 0
   ORDER BY entryTimestamp DESC LIMIT 1) AS weight,

  (SELECT bodyFat FROM daily_entries
   WHERE day = d.day AND bodyFat IS NOT NULL AND bodyFat > 0
   ORDER BY entryTimestamp DESC LIMIT 1) AS bodyFat,

  (SELECT muscleMass FROM daily_entries
   WHERE day = d.day AND muscleMass IS NOT NULL AND muscleMass > 0
   ORDER BY entryTimestamp DESC LIMIT 1) AS muscleMass,

  (SELECT water FROM daily_entries
   WHERE day = d.day AND water IS NOT NULL AND water > 0
   ORDER BY entryTimestamp DESC LIMIT 1) AS water,

  (SELECT bmi FROM daily_entries
   WHERE day = d.day AND bmi IS NOT NULL AND bmi > 0
   ORDER BY entryTimestamp DESC LIMIT 1) AS bmi,

  (SELECT bmr FROM daily_entries
   WHERE day = d.day AND bmr IS NOT NULL AND bmr > 0
   ORDER BY entryTimestamp DESC LIMIT 1) AS bmr,

  (SELECT metabolicAge FROM daily_entries
   WHERE day = d.day AND metabolicAge IS NOT NULL AND metabolicAge > 0
   ORDER BY entryTimestamp DESC LIMIT 1) AS metabolicAge,

  (SELECT proteinPercent FROM daily_entries
   WHERE day = d.day AND proteinPercent IS NOT NULL AND proteinPercent > 0
   ORDER BY entryTimestamp DESC LIMIT 1) AS proteinPercent,

  (SELECT pulse FROM daily_entries
   WHERE day = d.day AND pulse IS NOT NULL AND pulse > 0
   ORDER BY entryTimestamp DESC LIMIT 1) AS pulse,

  (SELECT skeletalMusclePercent FROM daily_entries
   WHERE day = d.day AND skeletalMusclePercent IS NOT NULL AND skeletalMusclePercent > 0
   ORDER BY entryTimestamp DESC LIMIT 1) AS skeletalMusclePercent,

  (SELECT subcutaneousFatPercent FROM daily_entries
   WHERE day = d.day AND subcutaneousFatPercent IS NOT NULL AND subcutaneousFatPercent > 0
   ORDER BY entryTimestamp DESC LIMIT 1) AS subcutaneousFatPercent,

  (SELECT visceralFatLevel FROM daily_entries
   WHERE day = d.day AND visceralFatLevel IS NOT NULL AND visceralFatLevel > 0
   ORDER BY entryTimestamp DESC LIMIT 1) AS visceralFatLevel,

  (SELECT boneMass FROM daily_entries
   WHERE day = d.day AND boneMass IS NOT NULL AND boneMass > 0
   ORDER BY entryTimestamp DESC LIMIT 1) AS boneMass,

  (SELECT impedance FROM daily_entries
   WHERE day = d.day AND impedance IS NOT NULL AND impedance > 0
   ORDER BY entryTimestamp DESC LIMIT 1) AS impedance
FROM distinct_days d
ORDER BY d.day DESC
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
            strftime('%Y-%m', datetime(e.entryTimestamp,${UTC},${LOCAL_TIME})) AS period
        FROM entry_view e
        LEFT JOIN body_scale_entry bse ON e.id = bse.id
        WHERE e.accountId = :accountId AND bse.weight IS NOT NULL
          AND (e.operationType IS NULL OR e.operationType != 'delete')
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
            first_entry.weight AS firstWeight,
            last_entry.weight AS lastWeight,
            (SELECT AVG(weight) FROM entries_with_period WHERE period = fl.period) AS avgWeight,
            (SELECT COUNT(*) FROM entries_with_period WHERE period = fl.period) AS entryCount
        FROM first_last fl
        LEFT JOIN entries_with_period first_entry ON fl.firstTimestamp = first_entry.entryTimestamp
        LEFT JOIN entries_with_period last_entry ON fl.lastTimestamp = last_entry.entryTimestamp
    )
    SELECT
        CASE CAST(strftime('%m', datetime(firstTimestamp, ${UTC}, ${LOCAL_TIME})) AS INTEGER)
            WHEN 1 THEN $MONTH_JAN
            WHEN 2 THEN $MONTH_FEB
            WHEN 3 THEN $MONTH_MAR
            WHEN 4 THEN $MONTH_APR
            WHEN 5 THEN $MONTH_MAY
            WHEN 6 THEN $MONTH_JUN
            WHEN 7 THEN $MONTH_JUL
            WHEN 8 THEN $MONTH_AUG
            WHEN 9 THEN $MONTH_SEP
            WHEN 10 THEN $MONTH_OCT
            WHEN 11 THEN $MONTH_NOV
            WHEN 12 THEN $MONTH_DEC
        END || ' ' || strftime('%Y', datetime(firstTimestamp, ${UTC}, ${LOCAL_TIME})) AS entryTimestamp,
        avgWeight,
        entryCount,
        CASE
            WHEN firstWeight IS NOT NULL AND lastWeight IS NOT NULL
            THEN lastWeight - firstWeight
            ELSE NULL
        END AS change
    FROM joined
    ORDER BY period DESC
    """,
  )
  fun getMonthlyHistory(
    accountId: String
  ): Flow<List<HistoryMonth>>

  /**
   * Get monthly history for an account for the last 365 days.
   * This method automatically filters entries from the last 365 days, groups by month, and calculates averages.
   * @param accountId The account ID
   * @return Flow of list of monthly history for the last 365 days
   */
  @Query(
    """
    WITH entries_with_period AS (
        SELECT
            e.entryTimestamp,
            bse.weight,
            strftime('%Y-%m', datetime(e.entryTimestamp,${UTC},${LOCAL_TIME})) AS period
        FROM entry_view e
        LEFT JOIN body_scale_entry bse ON e.id = bse.id
        WHERE e.accountId = :accountId
          AND bse.weight IS NOT NULL
          AND (e.operationType IS NULL OR e.operationType != 'delete')
          AND datetime(e.entryTimestamp) >= datetime('now', '-365 days')
          AND datetime(e.entryTimestamp) <= datetime('now')
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
            first_entry.weight AS firstWeight,
            last_entry.weight AS lastWeight,
            (SELECT AVG(weight) FROM entries_with_period WHERE period = fl.period) AS avgWeight,
            (SELECT COUNT(*) FROM entries_with_period WHERE period = fl.period) AS entryCount
        FROM first_last fl
        LEFT JOIN entries_with_period first_entry ON fl.firstTimestamp = first_entry.entryTimestamp
        LEFT JOIN entries_with_period last_entry ON fl.lastTimestamp = last_entry.entryTimestamp
    )
    SELECT
        CASE CAST(strftime('%m', datetime(firstTimestamp, ${UTC}, ${LOCAL_TIME})) AS INTEGER)
            WHEN 1 THEN $MONTH_JAN
            WHEN 2 THEN $MONTH_FEB
            WHEN 3 THEN $MONTH_MAR
            WHEN 4 THEN $MONTH_APR
            WHEN 5 THEN $MONTH_MAY
            WHEN 6 THEN $MONTH_JUN
            WHEN 7 THEN $MONTH_JUL
            WHEN 8 THEN $MONTH_AUG
            WHEN 9 THEN $MONTH_SEP
            WHEN 10 THEN $MONTH_OCT
            WHEN 11 THEN $MONTH_NOV
            WHEN 12 THEN $MONTH_DEC
        END || ' ' || strftime('%Y', datetime(firstTimestamp, ${UTC}, ${LOCAL_TIME})) AS entryTimestamp,
        avgWeight,
        entryCount,
        CASE
            WHEN firstWeight IS NOT NULL AND lastWeight IS NOT NULL
            THEN lastWeight - firstWeight
            ELSE NULL
        END AS change
    FROM joined
    ORDER BY period DESC
    """,
  )
  fun getMonthlyHistoryLastYear(
    accountId: String
  ): Flow<List<HistoryMonth>>

  /**
   * Get the oldest entry for an account.
   * @param accountId The account ID
   * @return The oldest entry if found, null otherwise
   */
  @Query("SELECT * FROM entry_view WHERE accountId = :accountId AND (operationType IS NULL OR operationType != 'delete') ORDER BY entryTimestamp ASC LIMIT 1")
  suspend fun getOldestEntry(accountId: String): PopulatedActiveEntry?

  /**
   * Get entry timestamps for streak calculation.
   * Returns one entry timestamp per day, ordered with newest first.
   * @param accountId The account ID
   * @return List of entry timestamps for streak calculation
   */
  @Query(
    """
         SELECT
        strftime('%Y-%m-%d', datetime(entryTimestamp,${UTC},${LOCAL_TIME}))
        FROM entry_view
        WHERE accountId = :accountId
          AND (operationType IS NULL OR operationType != 'delete')
        GROUP BY strftime('%Y-%m-%d', datetime(entryTimestamp,${UTC}, ${LOCAL_TIME}))
        ORDER BY datetime(entryTimestamp,${UTC},${LOCAL_TIME}) DESC
        """,
  )
  suspend fun getStreakData(accountId: String): List<String>

  /**
   * Get the total count of entries for an account.
   * @param accountId The account ID
   * @return The total count of entries
   */
  @Query("SELECT COUNT(entryTimestamp) as total FROM entry_view WHERE accountId = :accountId AND (operationType IS NULL OR operationType != 'delete')")
  suspend fun getTotalCount(accountId: String): Int

  /**
   * Get the longest streak count for an account.
   * Uses window function (row_number) + GROUP BY to compute max consecutive days; faster than recursive CTE for large datasets.
   * @param accountId The account ID
   * @return The longest streak count
   */
  @Query(
    """
        SELECT COALESCE(MAX(streak_count), 0) AS longestStreak FROM (
          SELECT COUNT(*) AS streak_count FROM (
            SELECT day_start, row_number() OVER (ORDER BY day_start) AS row_num FROM (
              SELECT strftime('%Y-%m-%d', datetime(entryTimestamp,${UTC},${LOCAL_TIME})) AS day_start
              FROM entry_view
              WHERE accountId = :accountId AND (operationType IS NULL OR operationType != 'delete')
              GROUP BY strftime('%Y-%m-%d', datetime(entryTimestamp,${UTC},${LOCAL_TIME}))
            )
          ) AS t1
          GROUP BY strftime('%Y-%m-%d', date(day_start, '-' || (row_num - 1) || ' day'))
        )
        """,
  )
  suspend fun getLongestStreakCount(accountId: String): Int

  /**
   * Get entries for an account in a specific date range (inclusive).
   * @param accountId The account ID
   * @param startDate The start date (ISO 8601 string)
   * @param endDate The end date (ISO 8601 string)
   * @return Flow of list of entries in the date range
   */
  @Transaction
  @Query(
    """
        SELECT * FROM entry_view
        WHERE accountId = :accountId
          AND (operationType IS NULL OR operationType != 'delete')
          AND entryTimestamp >= :startDate
          AND entryTimestamp <= :endDate
        ORDER BY datetime(entryTimestamp) DESC
    """,
  )
  fun getEntriesInRange(accountId: String, startDate: String, endDate: String): Flow<List<PopulatedActiveEntry>>

  @Transaction
  @Query(
    """
        SELECT * FROM entry_view
        WHERE accountId = :accountId
          AND (operationType IS NULL OR operationType != 'delete')
          AND entryTimestamp >= :startDate
        ORDER BY datetime(entryTimestamp) DESC
    """,
  )
  fun getEntriesSince(accountId: String, startDate: String): Flow<List<PopulatedActiveEntry>>
}
