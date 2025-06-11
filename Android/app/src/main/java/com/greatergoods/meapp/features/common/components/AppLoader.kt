package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.theme.MeAppTheme

enum class LoaderStyle {
    CIRCULAR,
    DASHED,
    DOT
}

sealed class LoaderConfig(open val color: Color) {
    data class Circular(
        override val color: Color,
        val strokeWidth: Dp,
        val size: Float
    ) : LoaderConfig(color)

    data class Dashed(
        override val color: Color,
        val size: Dp,
        val strokeWidth: Float,
        val dashLength: Float,
        val gapLength: Float,
        val sweepAngle: Float
    ) : LoaderConfig(color)

    data class Dot(
        override val color: Color,
        val minRadius: Float,
        val maxRadius: Float,
        val durationMillis: Int
    ) : LoaderConfig(color)
}

object LoaderDefaults {

    // Cached base configs (private, lazy to ensure one-time construction)
    private val baseDotConfig by lazy {
        LoaderConfig.Dot(
            color = Color(0xFF2C2827),
            minRadius = 6f,
            maxRadius = 12f,
            durationMillis = 700,
        )
    }

    private val baseDashedConfig by lazy {
        LoaderConfig.Dashed(
            color = Color(0xFF2C2827),
            size = 32.dp,
            strokeWidth = 4f,
            dashLength = 10f,
            gapLength = 8f,
            sweepAngle = 270f,
        )
    }

    private val baseCircularConfig by lazy {
        LoaderConfig.Circular(
            color = Color(0xFF6200EE),
            strokeWidth = 12.dp,
            size = 32f,
        )
    }

    // Public functions to return a modified version of the base config
    fun dotConfig(
        color: Color = baseDotConfig.color,
        minRadius: Float = baseDotConfig.minRadius,
        maxRadius: Float = baseDotConfig.maxRadius,
        durationMillis: Int = baseDotConfig.durationMillis
    ): LoaderConfig.Dot {
        return baseDotConfig.copy(
            color = color,
            minRadius = minRadius,
            maxRadius = maxRadius,
            durationMillis = durationMillis,
        )
    }

    fun dashedConfig(
        color: Color = baseDashedConfig.color,
        size: Dp = baseDashedConfig.size,
        strokeWidth: Float = baseDashedConfig.strokeWidth,
        dashLength: Float = baseDashedConfig.dashLength,
        gapLength: Float = baseDashedConfig.gapLength,
        sweepAngle: Float = baseDashedConfig.sweepAngle
    ): LoaderConfig.Dashed {
        return baseDashedConfig.copy(
            color = color,
            size = size,
            strokeWidth = strokeWidth,
            dashLength = dashLength,
            gapLength = gapLength,
            sweepAngle = sweepAngle,
        )
    }

    fun circularConfig(
        color: Color = baseCircularConfig.color,
        strokeWidth: Dp = baseCircularConfig.strokeWidth,
        size: Float = baseCircularConfig.size
    ): LoaderConfig.Circular {
        return baseCircularConfig.copy(
            color = color,
            strokeWidth = strokeWidth,
            size = size,
        )
    }

    fun defaultFor(style: LoaderStyle): LoaderConfig = when (style) {
        LoaderStyle.CIRCULAR -> baseCircularConfig
        LoaderStyle.DASHED -> baseDashedConfig
        LoaderStyle.DOT -> baseDotConfig
    }
}

/**
 * Displays a loader based on the given style and config. If config is not provided, uses LoaderDefaults.
 */
@Composable
fun AppLoader(
    modifier: Modifier = Modifier,
    isLoading: Boolean,
    label: String? = null,
    labelComposable: @Composable (() -> Unit)? = null,
    loaderStyle: LoaderStyle = LoaderStyle.CIRCULAR,
    loaderConfig: LoaderConfig = LoaderDefaults.defaultFor(loaderStyle),
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLoading) {
            when (loaderConfig) {
                is LoaderConfig.Circular -> {
                    CircularProgressIndicator(
                        color = loaderConfig.color,
                        strokeWidth = loaderConfig.strokeWidth,
                        modifier = modifier.size(loaderConfig.size.dp),
                    )
                }

                is LoaderConfig.Dashed -> {
                    DashedCircularLoader(
                        color = loaderConfig.color,
                        modifier = modifier.size(loaderConfig.size),
                        strokeWidth = loaderConfig.strokeWidth,
                        dashLength = loaderConfig.dashLength,
                        gapLength = loaderConfig.gapLength,
                        sweepAngle = loaderConfig.sweepAngle,
                    )
                }

                is LoaderConfig.Dot -> {
                    PulsingDotLoader(
                        color = loaderConfig.color,
                        modifier = modifier.size((loaderConfig.maxRadius * 2).dp),
                        minRadius = loaderConfig.minRadius,
                        maxRadius = loaderConfig.maxRadius,
                        durationMillis = loaderConfig.durationMillis,
                    )
                }
            }
        }
        if (label != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MeAppTheme.typography.heading5,
                color = MeAppTheme.colorScheme.primaryAction,
            )
        }
        if (labelComposable != null) {
            Spacer(modifier = Modifier.width(8.dp))
            labelComposable.invoke()
        }
    }
}
