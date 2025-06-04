package com.greatergoods.meapp.utils.browser

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class NavigationState {
    object Idle : NavigationState()
    object TabShown : NavigationState()
    object TabHidden : NavigationState()
    object Loading : NavigationState()
    object Finished : NavigationState()
    data class Failed(val error: Throwable?) : NavigationState()
}

@HiltViewModel
class CustomTabViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _navigationState = MutableStateFlow<NavigationState>(NavigationState.Idle)
    val navigationState: StateFlow<NavigationState> = _navigationState

    private var manager: CustomTabManager? = null

    init {
        manager = CustomTabManager(context, createListener())
    }

    fun launchTab(url: String) {
        viewModelScope.launch {
            val isBound = manager?.bindService() ?: false
            if (isBound) {
                manager?.openUrl(url, context)
            } else {
                _navigationState.value = NavigationState.Failed(Throwable("Failed to bind Custom Tabs service"))
            }
        }
    }

    private fun createListener() = object : CustomTabEventListener {
        override fun onTabShown() {
            _navigationState.value = NavigationState.TabShown
        }

        override fun onTabHidden() {
            _navigationState.value = NavigationState.TabHidden
        }

        override fun onNavigationStarted() {
            _navigationState.value = NavigationState.Loading
        }

        override fun onNavigationFinished() {
            _navigationState.value = NavigationState.Finished
        }

        override fun onNavigationFailed() {
            _navigationState.value = NavigationState.Failed(Throwable("Navigation Failed"))
        }
    }

    fun resetNavigationState() {
        _navigationState.value = NavigationState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        manager?.unbind()
    }
}

