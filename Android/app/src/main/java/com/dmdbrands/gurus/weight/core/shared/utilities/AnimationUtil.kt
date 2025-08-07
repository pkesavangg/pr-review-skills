package com.dmdbrands.gurus.weight.core.shared.utilities

import androidx.core.animation.doOnEnd
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AnticipateOvershootInterpolator

object AnimationUtil {
    // Splash screen exit animation utility
    fun splashScreenExitAnimation(
        splashScreenView: View,
        onEnd: () -> Unit = {},
    ) {
        val scaleX =
            ObjectAnimator.ofFloat(
                splashScreenView,
                View.SCALE_X,
                1f,
                1.2f,
                0.9f,
                1f,
            )
        val scaleY =
            ObjectAnimator.ofFloat(
                splashScreenView,
                View.SCALE_Y,
                1f,
                1.2f,
                0.9f,
                1f,
            )

        // 2. Fade out: alpha from 1 → 0
        val fadeOut =
            ObjectAnimator.ofFloat(
                splashScreenView,
                View.ALPHA,
                1f,
                0f,
            )

        // 3. Combine all with a clean, springy interpolator
        val animatorSet =
            AnimatorSet().apply {
                playTogether(scaleX, scaleY, fadeOut)
                interpolator = AnticipateOvershootInterpolator()
                duration = 600L

                // 4. Remove the splash screen view at the end
                doOnEnd { }
            }

        animatorSet.start()
    }
}
