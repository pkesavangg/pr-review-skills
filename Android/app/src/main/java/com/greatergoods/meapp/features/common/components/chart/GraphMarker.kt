package com.greatergoods.meapp.features.common.components.chart

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.common.component.fixed
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.insets
import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.core.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.core.common.component.ShapeComponent
import com.patrykandpatrick.vico.core.common.component.TextComponent
import com.patrykandpatrick.vico.core.common.shape.CorneredShape

class LayeredMarker(
    val marker: CartesianMarker,
    val decorations: List<Decoration>
) : CartesianMarker {
    override fun drawOverLayers(context: CartesianDrawingContext, targets: List<CartesianMarker.Target>) {
        marker.drawOverLayers(context, targets)
        decorations.first().drawOverLayers(context)
    }

    override fun drawUnderLayers(context: CartesianDrawingContext, targets: List<CartesianMarker.Target>) {

        marker.drawUnderLayers(context, targets)
        decorations.forEach { decoration ->
            decoration.drawUnderLayers(context)
        }
    }
}

@Composable
internal fun rememberDefaultMarker(
    valueFormatter: DefaultCartesianMarker.ValueFormatter =
        DefaultCartesianMarker.ValueFormatter.default(),
): CartesianMarker {
    val markerColor = Color(0xFF1565C0)
    val label =
        rememberTextComponent(
            color = markerColor,
            padding = insets(10.dp),
            minWidth = TextComponent.MinWidth.fixed(40.dp),
        )
    val guideline = rememberAxisLineComponent(
        fill = fill(Color(0xFFD0CCCA)), // light gray for grid lines
        thickness = 1.dp,
    )

    return rememberDefaultCartesianMarker(
        label = label,
        labelPosition = DefaultCartesianMarker.LabelPosition.Top,
        valueFormatter = valueFormatter,
        indicator = { color ->
            ShapeComponent(
                fill = fill(markerColor),
                strokeFill = fill(color),
                shape = CorneredShape.cut(20f),
                strokeThicknessDp = 2f,
            )
        },
        indicatorSize = 10.dp,
        guideline = guideline,
    )
}

@Composable
fun rememberMarker(
    valueFormatter: DefaultCartesianMarker.ValueFormatter =
        DefaultCartesianMarker.ValueFormatter.default(),
    decorations: List<Decoration>,
): CartesianMarker {
    val defaultMarker = rememberDefaultMarker(valueFormatter = valueFormatter)
    return LayeredMarker(
        defaultMarker,
        decorations,
    )
}
