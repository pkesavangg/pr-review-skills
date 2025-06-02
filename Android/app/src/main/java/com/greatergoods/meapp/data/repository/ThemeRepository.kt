package com.greatergoods.meapp.data.repository

import com.greatergoods.meapp.data.storage.datastore.ThemeDataStore
import com.greatergoods.meapp.data.storage.datastore.ThemeMode
import jakarta.inject.Inject
import kotlinx.coroutines.flow.Flow

class ThemeRepository
    @Inject
    constructor(
        private val themeDataStore: ThemeDataStore,
    ) {
        val themeModeFlow: Flow<ThemeMode> = themeDataStore.themeModeFlow

        suspend fun setThemeMode(mode: ThemeMode) {
            themeDataStore.setThemeMode(mode)
        }
    }
