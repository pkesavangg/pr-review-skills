package com.dmdbrands.gurus.weight.core.power

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

/**
 * Power-Saving-Mode-aware replacement for an infinite [animateFloat].
 *
 * When Battery Saver is on (see [LocalPowerSaveMode]) this skips creating the infinite
 * transition entirely and returns [restingValue] — a single static frame — so the
 * OS-throttled CPU is not spent redrawing a looping animation, which is what makes the
 * app feel sluggish in Power Saving Mode (MOB-226). When Battery Saver is off it behaves
 * exactly like a normal infinite [animateFloat].
 *
 * @param initialValue start value of the animation when running.
 * @param targetValue end value of the animation when running.
 * @param animationSpec the repeatable spec used when running.
 * @param restingValue the static value to display while Power Saving Mode is on. Defaults to
 *   [targetValue]; callers should pass the value that looks correct as a still frame
 *   (e.g. 0f for a wiggle, the mid-point for a shimmer).
 * @param label animation label for tooling.
 */
@Composable
fun powerSaveAwareInfiniteFloat(
  initialValue: Float,
  targetValue: Float,
  animationSpec: InfiniteRepeatableSpec<Float>,
  restingValue: Float = targetValue,
  label: String = "PowerSaveAwareFloat",
): Float {
  if (LocalPowerSaveMode.current) return restingValue
  val transition = rememberInfiniteTransition(label = label)
  val value by transition.animateFloat(
    initialValue = initialValue,
    targetValue = targetValue,
    animationSpec = animationSpec,
    label = label,
  )
  return value
}
