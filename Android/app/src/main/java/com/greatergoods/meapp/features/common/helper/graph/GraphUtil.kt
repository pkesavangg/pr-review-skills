package com.greatergoods.meapp.features.common.helper.graph

import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryMetricEntity
import com.greatergoods.meapp.domain.model.storage.entry.ScaleEntry
import com.greatergoods.meapp.features.common.model.chart.GraphLine
import com.greatergoods.meapp.features.common.model.chart.GraphPoint
import com.greatergoods.meapp.features.common.model.chart.Label
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

object GraphUtil {
    fun List<ScaleEntry>.toWeightGraphPoints(): GraphLine {
        return GraphLine(name = "Weight", points = this.map { entry ->
            GraphPoint(
                x = Label(
                    value = entry.entry.entryTimestamp.toFloat(),
                    label = entry.entry.entryTimestamp.toString(),
                ),
                y = Label(value = entry.scale.scaleEntry.weight, label = "${entry.scale.scaleEntry.weight} kg"),
            )
        }
        )
    }

    fun List<ScaleEntry>.toGraphPoints(propertyName: String): GraphLine {
        val scaleProps = BodyScaleEntryEntity::class.memberProperties
        val metricProps = BodyScaleEntryMetricEntity::class.memberProperties

        val scaleProp = scaleProps.find { it.name == propertyName } as? KProperty1<BodyScaleEntryEntity, *>
        val metricProp = metricProps.find { it.name == propertyName } as? KProperty1<BodyScaleEntryMetricEntity, *>

        return GraphLine(
            name = propertyName, points = this.mapNotNull { scaleEntry ->
            val value: Float? = when {
                scaleProp != null -> (scaleProp.get(scaleEntry.scale.scaleEntry) as? Number)?.toFloat()
                metricProp != null -> scaleEntry.scale.scaleEntryMetric?.let {
                    (metricProp.get(it) as? Number)?.toFloat()
                }

                else -> null
            }

            value?.let {
                val xValue = scaleEntry.entry.entryTimestamp.toFloatOrNull() ?: return@mapNotNull null
                GraphPoint(
                    x = Label(value = xValue, label = scaleEntry.entry.entryTimestamp),
                    y = Label(value = it, label = "$it"),
                )
            }
        }
        )
    }
}
