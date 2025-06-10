package com.greatergoods.meapp.features.common.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
    ) : LoaderConfig(color)

    data class Dot(
        override val color: Color,
    ) : LoaderConfig(color)
}

object LoaderDefaults {

    // Cached base configs (private, lazy to ensure one-time construction)
    private val baseDotConfig by lazy {
        LoaderConfig.Dot(
            color = Color(0xFF2C2827),
        )
    }

    private val baseDashedConfig by lazy {
        LoaderConfig.Dashed(
            color = Color(0xFF2C2827),
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
    ): LoaderConfig.Dot {
        return baseDotConfig.copy(
            color = color,
        )
    }

    fun dashedConfig(
        color: Color = baseDashedConfig.color,
    ): LoaderConfig.Dashed {
        return baseDashedConfig.copy(
            color = color,
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
    isLoading: Boolean,
    loaderStyle: LoaderStyle = LoaderStyle.CIRCULAR,
    loaderConfig: LoaderConfig = LoaderDefaults.defaultFor(loaderStyle),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (isLoading) {
        when (loaderConfig) {
            is LoaderConfig.Circular -> {
            }

            is LoaderConfig.Dashed -> {
            }

            is LoaderConfig.Dot -> {
            }
        }
    } else {
        content()
    }
}
