package com.dmdbrands.gurus.weight.theme.model

import androidx.compose.runtime.Stable

@Stable
data class Animation(
    val shortDuration: Int,
    val mediumDuration: Int,
    val longDuration: Int,
    val easingCurve: Float
)
