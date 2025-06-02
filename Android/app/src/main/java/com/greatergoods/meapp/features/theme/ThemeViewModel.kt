package com.greatergoods.meapp.features.theme

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.data.repository.ThemeRepository
import com.greatergoods.meapp.data.storage.datastore.ThemeMode
import com.greatergoods.meapp.features.common.viewmodel.NavigationViewmodel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val themeRepository: ThemeRepository
) : NavigationViewmodel() {

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode

    init {
        viewModelScope.launch {
            themeRepository.themeModeFlow.collectLatest {
                _themeMode.value = it
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            themeRepository.setThemeMode(mode)
        }
    }
}
