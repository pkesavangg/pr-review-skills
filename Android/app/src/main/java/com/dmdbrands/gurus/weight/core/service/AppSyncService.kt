package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.api.entry.ScaleApiEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IAppSyncService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.features.common.model.Toast
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service implementation for handling AppSync-related business logic
 */
@Singleton
class AppSyncService @Inject constructor(
  private val entryService: IEntryService,
  private val appNavigationService: IAppNavigationService,
  private val dialogQueueService: IDialogQueueService,
  private val userDataStore: UserDataStore,
) : IAppSyncService {

  private val _appSyncDataForEditing = MutableStateFlow<ScaleEntry?>(null)
  override val appSyncDataForEditing: StateFlow<ScaleEntry?> = _appSyncDataForEditing.asStateFlow()

  private val _appSyncData = MutableStateFlow<ScaleApiEntry?>(null)
  override val appSyncData: StateFlow<ScaleApiEntry?> = _appSyncData.asStateFlow()

  override val lastZoomLevel: Flow<Int> = userDataStore.lastAppSyncZoomLevelFlow

  override suspend fun saveLastZoomLevel(zoom: Int) {
    userDataStore.setLastAppSyncZoomLevel(zoom)
  }

  override suspend fun setAppSyncDataForEditing(scaleEntry: ScaleEntry?) {
    _appSyncDataForEditing.value = scaleEntry
    AppLog.d("AppSyncService", "AppSync data set for editing")
  }

  override suspend fun setAppSyncData(scaleApiEntry: ScaleApiEntry?) {
    _appSyncData.value = scaleApiEntry
  }

  override suspend fun handleEditAppSyncData(scaleEntry: ScaleEntry) {
    try {
      setAppSyncDataForEditing(scaleEntry)
      // setAppSyncData(scaleEntry)
      appNavigationService.navigateTo(AppRoute.Main.Entry, AppRoute.Home)
      AppLog.d("AppSyncService", "Successfully navigated to manual entry with AppSync data")
    } catch (e: Exception) {
      AppLog.e("AppSyncService", "Error handling edit AppSync data: ${e.message}", e)
      dialogQueueService.showToast(
        Toast.Simple(message = "Failed to navigate to manual entry: ${e.message}"),
      )
    }
  }

  override suspend fun handleSaveAppSyncData(scaleEntry: ScaleEntry): Boolean {
    return try {
      entryService.addEntry(scaleEntry)
      setAppSyncDataForEditing(null)
      AppLog.d("AppSyncService", "Successfully saved AppSync entry")
      true
    } catch (e: Exception) {
      AppLog.e("AppSyncService", "Error saving AppSync entry: ${e.message}", e)
      dialogQueueService.showToast(
        Toast.Simple(message = "Failed to save entry: ${e.message}"),
      )
      false
    }
  }
}
