package com.dmdbrands.gurus.weight.theme.token

import androidx.compose.runtime.staticCompositionLocalOf
import com.dmdbrands.gurus.weight.theme.model.Animation

val AnimationToken =
    Animation(
        shortDuration = 150,
        mediumDuration = 300,
        longDuration = 600,
        easingCurve = 0.4f,
    )

val LocalAnimation =
    staticCompositionLocalOf<Animation> {
        AnimationToken
    }
