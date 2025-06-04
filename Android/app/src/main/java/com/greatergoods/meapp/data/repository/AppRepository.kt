package com.greatergoods.meapp.data.repository

import com.greatergoods.meapp.data.storage.datastore.FcmDataStore
import com.greatergoods.meapp.data.storage.datastore.UserDataStore
import com.greatergoods.meapp.domain.repository.IAppRepository
import com.greatergoods.meapp.proto.ThemeMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Implementation of [IAppRepository] for app-wide data operations.
 */
@Singleton
class AppRepository @Inject constructor(
    private val userDataStore: UserDataStore,
    private val fcmDataStore: FcmDataStore
) : IAppRepository {

    override val themeModeFlow: Flow<ThemeMode> = userDataStore.currentThemeModeFlow

    override val fcmTokenFlow: Flow<String> = fcmDataStore.tokenFlow

    override suspend fun getThemeMode(): ThemeMode = userDataStore.getCurrentThemeMode()

    /**
     * Sets the theme mode for a specific account.
     * @param accountId The account ID to update.
     * @param mode The ThemeMode to set.
     */
    override suspend fun setThemeMode(accountId: String, mode: ThemeMode) {
        userDataStore.setThemeMode(accountId, mode)
    }

    override suspend fun clearThemeMode() {
        userDataStore.clearData()
    }

    override suspend fun getFcmToken(): String = fcmDataStore.getData().token

    override suspend fun setFcmToken(token: String) {
        fcmDataStore.setToken(token)
    }

    override suspend fun clearFcmToken() {
        fcmDataStore.clearData()
    }
}
