package com.greatergoods.meapp.data.storage.db.dao

import androidx.room.*
import com.greatergoods.meapp.data.storage.db.entity.ScaleEntryMetricEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for the scale_entry_metric table.
 * Provides methods to interact with scale entry metric data in the database.
 */
@Dao
interface ScaleEntryMetricDao {
    /**
     * Insert a new scale entry metric into the database.
     * @param metric The scale entry metric entity to insert
     * @return The row ID of the inserted entry
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metric: ScaleEntryMetricEntity): Long

    /**
     * Update an existing scale entry metric in the database.
     * @param metric The scale entry metric entity to update
     * @return The number of rows updated
     */
    @Update
    suspend fun update(metric: ScaleEntryMetricEntity): Int

    /**
     * Delete a scale entry metric from the database.
     * @param metric The scale entry metric entity to delete
     * @return The number of rows deleted
     */
    @Delete
    suspend fun delete(metric: ScaleEntryMetricEntity): Int

    /**
     * Get a scale entry metric by its ID.
     * @param id The scale entry metric ID
     * @return The scale entry metric entity if found, null otherwise
     */
    @Query("SELECT * FROM scale_entry_metric WHERE id = :id")
    suspend fun getScaleEntryMetricById(id: Long): ScaleEntryMetricEntity?

    /**
     * Get all scale entry metrics for a specific user.
     * @param userId The user ID
     * @return A Flow of all scale entry metrics for the user
     */
    @Query("SELECT * FROM scale_entry_metric WHERE id IN (SELECT id FROM scale_entry WHERE id IN (SELECT id FROM entry WHERE userId = :userId))")
    fun getScaleEntryMetricsByUserId(userId: String): Flow<List<ScaleEntryMetricEntity>>

    /**
     * Delete all scale entry metrics for a specific user.
     * @param userId The user ID
     * @return The number of rows deleted
     */
    @Query("DELETE FROM scale_entry_metric WHERE id IN (SELECT id FROM scale_entry WHERE id IN (SELECT id FROM entry WHERE userId = :userId))")
    suspend fun deleteAllScaleEntryMetricsForUser(userId: String): Int
} 