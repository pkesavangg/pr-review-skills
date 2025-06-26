package com.greatergoods.meapp.features.common.components.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.features.common.enums.GraphSegment
import com.greatergoods.meapp.theme.MeTheme
import com.patrykandpatrick.vico.compose.cartesian.layer.continuous
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.shape.CorneredShape

/**
 * Internal helper to remember the primary layer for the graph.
 */
@Composable
internal fun primaryLayer(segment: GraphSegment): LineCartesianLayer {
    return rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(
            listOf(MeTheme.colorScheme.primaryAction).map {
                LineCartesianLayer.rememberLine(
                    fill = LineCartesianLayer.LineFill.single(fill(it)),
                    stroke = LineCartesianLayer.LineStroke.continuous(thickness = 3.dp),
                    pointConnector = LineCartesianLayer.PointConnector.cubic(0.5f),
                    pointProvider = LineCartesianLayer.PointProvider.single(
                        point = LineCartesianLayer.Point(
                            rememberShapeComponent(
                                fill(it),
                                CorneredShape.Pill,
                                strokeThickness = 2.dp,
                            ),
                        ),
                    ),
                )
            },
        ),
        pointSpacing = pointSpacing(segment, 10.dp),
    )
}

/**
 * Calculates the point spacing for the graph based on the segment and axis padding.
 * UI-only logic, not business/data transformation.
 */
@Composable
private fun pointSpacing(
    segment: GraphSegment,
    axisPadding: Dp = 0.dp
): Dp {
    val windowInfo = LocalWindowInfo.current
    val screenWidthPx = windowInfo.containerSize.width
    val density = LocalDensity.current
    val intervalCount = remember(segment) {
        when (segment) {
            GraphSegment.WEEK -> 7
            GraphSegment.MONTH -> 6
            GraphSegment.YEAR -> 12
            else -> 32
        }
    }
    return remember(segment, screenWidthPx, intervalCount, axisPadding) {
        with(density) { (screenWidthPx / intervalCount).toDp() - axisPadding }
    }
}
