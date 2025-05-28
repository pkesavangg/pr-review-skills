package com.greatergoods.meapp.theme.token

import androidx.compose.runtime.staticCompositionLocalOf
import com.greatergoods.meapp.theme.model.Animation

val AnimationTokens = Animation(
          shortDuration = 150,
          mediumDuration = 300,
          longDuration = 600,
          easingCurve = 0.4f,
)

val LocalAnimationTokens = staticCompositionLocalOf<Animation> {
    AnimationTokens
}