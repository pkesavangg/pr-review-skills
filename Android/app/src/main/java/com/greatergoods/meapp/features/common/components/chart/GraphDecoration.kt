package com.greatergoods.meapp.features.common.components.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.theme.MeAppTheme
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.component.shapeComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.insets
import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.core.cartesian.decoration.HorizontalLine
import com.patrykandpatrick.vico.core.common.Position
import com.patrykandpatrick.vico.core.common.component.TextComponent
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import android.graphics.Typeface
import android.text.Layout

@Composable
fun rememberHorizontalLine(key: ExtraStore.Key<List<Double>>, markerIndex: Int): Decoration {
    val fill = fill(Color(0xFF458239))
    val line = rememberLineComponent(fill = fill, thickness = 2.dp)
    val labelComponent =
        rememberTextComponent(
            typeface = Typeface.DEFAULT_BOLD,
            color = MeAppTheme.colorScheme.primary,
            lineHeight = MeAppTheme.typography.body3.lineHeight,
            textSize = MeAppTheme.typography.body3.fontSize,
            textAlignment = Layout.Alignment.ALIGN_CENTER,
            margins = insets(start = 6.dp, top = 6.dp),
            padding = insets(horizontal = 8.dp, vertical = 4.dp),
            background =
                shapeComponent(
                    fill,
                    shape = CorneredShape.Pill,
                ),
            minWidth = TextComponent.MinWidth.fixed(40f),
        )

    val decoration = object : Decoration {
        override fun drawOverLayers(context: CartesianDrawingContext) {
            HorizontalLine(
                y = { it[key][markerIndex] },
                line = line.copy(fill = fill(Color.Transparent)),
                labelComponent = labelComponent,
                horizontalLabelPosition = Position.Horizontal.End,
                verticalLabelPosition = Position.Vertical.Bottom,
                verticalAxisPosition = Axis.Position.Vertical.Start,
            ).drawOverLayers(context)
        }

        override fun drawUnderLayers(context: CartesianDrawingContext) {
            HorizontalLine(
                y = { it[key][markerIndex] },
                line = line,
            ).drawOverLayers(context)
        }
    }
    return remember(markerIndex) { decoration }
}
