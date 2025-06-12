package com.greatergoods.meapp.features.common.helper.graph

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryMetricEntity
import com.greatergoods.meapp.domain.model.storage.entry.PeriodBodyScaleSummary
import com.greatergoods.meapp.domain.model.storage.entry.ScaleEntry
import com.greatergoods.meapp.features.common.enum.GraphSegment
import com.greatergoods.meapp.features.common.model.chart.GraphLine
import com.greatergoods.meapp.features.common.model.chart.GraphPoint
import com.greatergoods.meapp.features.common.model.chart.Label
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

const val oneDayMillis = 24 * 60 * 60 * 1000L // 86,400,000 milliseconds

object GraphUtil {
    private val dateFormatter = DateTimeFormatter.ofPattern("MMM-dd-yyyy")
    fun List<PeriodBodyScaleSummary>.toWeightGraphPoints(): GraphLine {
        return GraphLine(
            name = "Weight",
            points = this.map { entry ->
                GraphPoint(
                    x = Label(
                        value = entry.entryTimestamp,
                        label = entry.period,
                    ),
                    y = Label(value = entry.weight, label = "${entry.weight} kg"),
                )
            },
        )

    fun List<ScaleEntry>.toGraphPoints(propertyName: String): GraphLine {
        val scaleProps = BodyScaleEntryEntity::class.memberProperties
        val metricProps = BodyScaleEntryMetricEntity::class.memberProperties

        val scaleProp = scaleProps.find { it.name == propertyName } as? KProperty1<BodyScaleEntryEntity, *>
        val metricProp = metricProps.find { it.name == propertyName } as? KProperty1<BodyScaleEntryMetricEntity, *>

        return GraphLine(
            name = propertyName,
            points = this.mapNotNull { scaleEntry ->
                val value: Float? = when {
                    scaleProp != null -> (scaleProp.get(scaleEntry.scale.scaleEntry) as? Number)?.toFloat()
                    metricProp != null -> scaleEntry.scale.scaleEntryMetric?.let {
                        (metricProp.get(it) as? Number)?.toFloat()
                    }

                    else -> null
                }

                value?.let {
                    val xValue = scaleEntry.entry.entryTimestamp
                    val dateStr = Instant.ofEpochMilli(xValue)
                        .atZone(ZoneId.of("America/Los_Angeles"))
                        .format(dateFormatter)
                    GraphPoint(
                        x = Label(value = xValue, label = dateStr),
                        y = Label(value = it, label = "$it"),
                    )
                }
            },
        )
    }

    @Composable
    fun rememberXStep(segment: GraphSegment): Double {
        return remember(segment) {
            when (segment) {
                GraphSegment.WEEK -> oneDayMillis
                GraphSegment.MONTH -> 8 * oneDayMillis
                GraphSegment.YEAR, GraphSegment.TOTAL -> 30 * oneDayMillis
            }.toDouble()
        }
    }

    @Composable
    fun rememberPointSpacing(
        segment: GraphSegment,
        axisPadding: Dp = 0.dp
    ): Dp {
        val windowInfo = LocalWindowInfo.current
        val screenWidthPx = windowInfo.containerSize.width
        val intervalCount = segment.intervalCount()

        val density = LocalDensity.current

        return remember(segment, screenWidthPx) {
            with(density) {
                (screenWidthPx / intervalCount).toDp()
            } - axisPadding
        }
    }

    private fun GraphSegment.intervalCount(): Int = when (this) {
        GraphSegment.WEEK -> 7
        GraphSegment.MONTH -> 4
        GraphSegment.YEAR, GraphSegment.TOTAL -> 12
    }
}


