package com.greatergoods.meapp.data.repository

import com.greatergoods.meapp.data.storage.datastore.FcmDataStore
import com.greatergoods.meapp.data.storage.datastore.ThemeDataStore
import com.greatergoods.meapp.data.storage.datastore.ThemeMode
import com.greatergoods.meapp.domain.repository.IAppRepository
import jakarta.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Implementation of [IAppRepository] for app-wide data operations.
 */
class AppRepository @Inject constructor(
    private val themeDataStore: ThemeDataStore,
    private val fcmDataStore: FcmDataStore
) : IAppRepository {

    override val themeModeFlow: Flow<ThemeMode> = themeDataStore.themeModeFlow
    override val fcmTokenFlow: Flow<String> = fcmDataStore.tokenFlow

    override suspend fun getThemeMode(): ThemeMode = themeDataStore.getData().mode

    override suspend fun setThemeMode(mode: ThemeMode) {
        themeDataStore.setThemeMode(mode)
    }

    override suspend fun clearThemeMode() {
        themeDataStore.clearData()
    }

    override suspend fun getFcmToken(): String = fcmDataStore.getData().token

    override suspend fun setFcmToken(token: String) {
        fcmDataStore.setToken(token)
    }

    override suspend fun clearFcmToken() {
        fcmDataStore.clearData()
    }
}
