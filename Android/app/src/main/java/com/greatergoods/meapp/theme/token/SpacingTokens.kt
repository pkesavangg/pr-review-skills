package com.greatergoods.meapp.theme.token

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.theme.model.Spacing

val SpacingToken = Spacing(
    tiny = 4.dp,
    small = 8.dp,
    medium = 16.dp,
    large = 24.dp,
)

val LocalSpacing = staticCompositionLocalOf<Spacing> {
    SpacingToken
}
