package com.greatergoods.meapp.core.shared.utilities.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomTabViewModel
    @Inject
    constructor(
        private val customTabManager: ICustomTabManager,
    ) : ViewModel() {
        private val _chromeTabState = MutableStateFlow<ChromeTabState>(ChromeTabState.Idle)
        val chromeTabState: StateFlow<ChromeTabState> = _chromeTabState

        init {
            viewModelScope.launch {
                customTabManager.subscribeChromeState().collect {
                    if (it != null) {
                        _chromeTabState.value = it
                    }
                }
            }
        }

        fun launchTab(url: String) {
            viewModelScope.launch {
                customTabManager.openChromeTab(url)
            }
        }

        override fun onCleared() {
            super.onCleared()
            customTabManager.unbind()
        }
    }
