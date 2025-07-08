package com.greatergoods.meapp.core.service

import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.data.storage.datastore.BaseProtoDataStore
import com.greatergoods.meapp.data.storage.db.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context

/**
 * Service responsible for clearing all app storage including database, DataStore, shared preferences, and cache.
 */
@Singleton
class StorageClearService
  @Inject
  constructor(
    @ApplicationContext private val context: Context,
    private val appDatabase: AppDatabase,
    private val dataStores: Set<@JvmSuppressWildcards BaseProtoDataStore<*>>,
    private val navigationService: IAppNavigationService,
  ) {
    private val tag = "StorageClearService"

    /**
     * Extension function to clear all local storage including database, DataStore, shared preferences, and cache.
     */
    private suspend fun Context.clearAllLocalData() =
      withContext(Dispatchers.IO) {
        try {
          AppLog.i(tag, "Starting to clear all local data")
          appDatabase.clearAllTables()
          clearAllDataStores()
        } catch (e: Exception) {
          AppLog.e(tag, "Failed to clear local data", e.toString())
          throw e
        }
      }

    /**
     * Clears all Proto DataStore instances.
     */
    private suspend fun clearAllDataStores() {
      try {
        AppLog.i(tag, "Clearing all DataStores (${dataStores.size} instances)")
        dataStores.forEach { dataStore ->
          try {
            dataStore.clearData()
          } catch (e: Exception) {
            AppLog.e(tag, "Failed to clear DataStore: ${dataStore::class.simpleName}", e.toString())
            // Continue with other DataStores even if one fails
          }
        }
        AppLog.i(tag, "All DataStores cleared successfully")
      } catch (e: Exception) {
        AppLog.e(tag, "Failed to clear DataStores", e.toString())
        throw e
      }
    }

    /**
     * Public method to clear all local storage.
     */
    suspend fun clearAllStorage() {
      context.clearAllLocalData()
    }
  }
