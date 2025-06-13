package com.greatergoods.meapp.features.common.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class PickerState<T>(initialValue: T) {
    private var _selectedItem by mutableStateOf(initialValue)

    fun setItem(item: T) {
        _selectedItem = item
    }

    val item: T
        get() = _selectedItem

}
