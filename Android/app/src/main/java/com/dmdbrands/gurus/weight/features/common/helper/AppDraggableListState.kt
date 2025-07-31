package com.dmdbrands.gurus.weight.features.common.helper

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class AppDraggableListState(initialIndex: Int = -1) {
    var openCardIndex by mutableIntStateOf(initialIndex)

    fun set(index: Int) {
        openCardIndex = if (openCardIndex == index) -1 else index
    }
}

@Composable
fun rememberAppDraggableListState() = remember { AppDraggableListState() } 