package com.dmdbrands.gurus.weight.domain.services

import com.dmdbrands.gurus.weight.domain.model.api.entry.ScaleApiEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Service interface for handling AppSync-related business logic
 */
interface IAppSyncService {
    /**
     * Handles editing AppSync data by navigating to manual entry
     */
    suspend fun handleEditAppSyncData(scaleEntry: ScaleEntry)

    /**
     * Handles saving AppSync data directly.
     * @return true if the entry was persisted successfully, false otherwise.
     */
    suspend fun handleSaveAppSyncData(scaleEntry: ScaleEntry): Boolean

    /**
     * Sets AppSync data for manual entry editing
     */
    suspend fun setAppSyncDataForEditing(scaleEntry: ScaleEntry?)

  /**
   * Sets AppSync data for manual entry editing
   */
  suspend fun setAppSyncData(scaleApiEntry: ScaleApiEntry?)

  /**
   * Sets AppSync data for manual entry editing
   */
    val appSyncData: StateFlow<ScaleApiEntry?>

    /**
     * Gets AppSync data for manual entry editing
     */
    val appSyncDataForEditing: StateFlow<ScaleEntry?>

    /**
     * Flow of the last AppSync zoom level for the active account.
     */
    val lastZoomLevel: Flow<Int>

    /**
     * Saves the last AppSync zoom level for the active account.
     */
    suspend fun saveLastZoomLevel(zoom: Int)
}
