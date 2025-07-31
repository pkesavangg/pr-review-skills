package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker

@Composable
internal fun emptyMarker(): CartesianMarker {
    val emptyFormatter = emptyFormatter()
    return rememberDefaultCartesianMarker(
        label = rememberTextComponent(color = MeTheme.colorScheme.textSubheading),
        valueFormatter = emptyFormatter,
        indicator = null,
    )
}

/**
 * Internal helper to remember the empty value formatter for the marker.
 */
@Composable
private fun emptyFormatter(): DefaultCartesianMarker.ValueFormatter =
    remember {
        object : DefaultCartesianMarker.ValueFormatter {
            override fun format(
                context: CartesianDrawingContext,
                targets: List<CartesianMarker.Target>,
            ) = ""
        }
    }
