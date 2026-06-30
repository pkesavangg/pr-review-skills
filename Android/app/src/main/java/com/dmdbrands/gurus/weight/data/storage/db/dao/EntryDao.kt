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
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BabyEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BpmEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PopulatedActiveEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PopulatedEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry

/** Projection of a weight entry's timestamp + stored note for batch note merging (MOB-438). */
data class DeviceNoteRow(val entryTimestamp: String, val note: String?)

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

    when (entry) {
      is BpmEntry -> insertBpm(entry.bpmEntry.copy(id = entryId))
      is ScaleEntry -> {
        insertBodyScale(entry.scale.scaleEntry.copy(id = entryId))
        if (entry.scale.scaleEntryMetric != null) {
          insertBodyScaleMetric(entry.scale.scaleEntryMetric.copy(id = entryId))
        }
      }
      // Without this branch the parent entry row was written but the baby_entry child row was
      // dropped, losing babyId/weight for scale-assigned readings (MOB-598).
      is BabyEntry -> insertBabyEntry(entry.babyEntry.copy(id = entryId))
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
        is BpmEntry -> insertBpm(entry.bpmEntry.copy(id = entryId))
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
    // NOTE: @Update returns the number of rows affected, NOT the row id — so the child
    // rows must be keyed by the entry's actual id, not the update() result (MOB-438).
    update(entry.entry)
    val id = entry.entry.id

    when (entry) {
      is BpmEntry -> updateBpm(entry.bpmEntry.copy(id = id))
      is ScaleEntry -> {
        updateBodyScale(entry.scale.scaleEntry.copy(id = id))
        if (entry.scale.scaleEntryMetric != null) {
          updateBodyScaleMetric(entry.scale.scaleEntryMetric.copy(id = id))
        }
      }
      // insertBabyEntry is a REPLACE upsert keyed by id, so it doubles as the update path (MOB-598).
      is BabyEntry -> insertBabyEntry(entry.babyEntry.copy(id = id))
    }
    return id
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
   * Get all entries with their related details for a specific account.
   * This method uses @Transaction to ensure all related data is fetched atomically.
   * @param accountId The account ID
   * @return List of Entry containing entries and their related data for the account
   */
  @Transaction
  @Query("SELECT * FROM entry_view WHERE accountId = :accountId AND (operationType IS NULL OR operationType != 'delete')")
  suspend fun getEntriesByAccount(accountId: String): List<PopulatedActiveEntry>


  /**
   * Get an entry by its ID with all related details.
   * @param id The entry ID
   * @return The Entry with relations if found, null otherwise
   */
  @Transaction
  @Query("SELECT * FROM entry WHERE id = :id AND (operationType IS NULL OR operationType != 'delete')")
  suspend fun getEntryById(id: Long): PopulatedEntry?

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
   * Returns the existing stored note for a weight entry matched by account + timestamp,
   * used to preserve a locally-entered note across a server sync that doesn't carry it
   * (MOB-438). Returns null when no row or no note exists.
   */
  @Query(
    "SELECT bse.note FROM entry e INNER JOIN body_scale_entry bse ON e.id = bse.id " +
      "WHERE e.accountId = :accountId AND e.entryTimestamp = :timestamp ORDER BY e.id DESC LIMIT 1",
  )
  suspend fun getStoredScaleNote(accountId: String, timestamp: String): String?

  /**
   * Batch variant of [getStoredScaleNote]: all non-blank weight notes for an account in a
   * single query, used to merge local notes during a bulk sync insert without issuing one
   * query per entry (MOB-438 PR review).
   */
  @Query(
    "SELECT e.entryTimestamp AS entryTimestamp, bse.note AS note " +
      "FROM entry e INNER JOIN body_scale_entry bse ON e.id = bse.id " +
      "WHERE e.accountId = :accountId AND bse.note IS NOT NULL AND bse.note != ''",
  )
  suspend fun getStoredScaleNotes(accountId: String): List<DeviceNoteRow>

  /**
   * Note-only updates (MOB-438). These touch just the note column so editing a note never
   * round-trips weight/metrics through unit conversions (which would corrupt the value).
   */
  @Query("UPDATE body_scale_entry SET note = :note WHERE id = :id")
  suspend fun updateScaleNote(id: Long, note: String?)

  @Query("UPDATE bpm_entry SET note = :note WHERE id = :id")
  suspend fun updateBpmNote(id: Long, note: String?)

  @Query("UPDATE baby_entry SET entryNote = :note WHERE id = :id")
  suspend fun updateBabyNote(id: Long, note: String?)

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
   * Get the operation count for an account.
   * @param accountId The account ID
   * @return The number of operations
   */
  @Query("SELECT COUNT(*) FROM entry WHERE accountId = :accountId")
  suspend fun getOperationCount(accountId: String): Int


  /**
   * Insert a list of scale entries into the database.
   * @param entries The list of BodyScaleEntryEntity objects to insert
   */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertScaleEntries(entries: List<BodyScaleEntryEntity>)





}
