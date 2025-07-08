package com.greatergoods.meapp.features.scaleDisplayMetrics.Helper

import com.greatergoods.meapp.features.scaleDisplayMetrics.model.ScaleMetric
import com.greatergoods.meapp.features.scaleDisplayMetrics.model.otherScaleMetrics
import com.greatergoods.meapp.features.scaleDisplayMetrics.model.scaleMetrics

/**
 * Helper object providing utility functions for display metrics management.
 */
object DisplayMetricsHelper {

    /**
     * Creates ordered metric states based on current metrics from the scale.
     * 
     * @param currentMetrics Current ordered list of metric keys from the scale.
     * @return Pair of ordered body metrics and other metrics with proper enabled states.
     */
    fun createOrderedMetrics(currentMetrics: List<String>): Pair<List<ScaleMetric>, List<ScaleMetric>> {
        val allBodyMetrics = scaleMetrics.associateBy { it.key }
        val allOtherMetrics = otherScaleMetrics.associateBy { it.key }

        val orderedBodyMetrics = mutableListOf<ScaleMetric>()
        val orderedOtherMetrics = mutableListOf<ScaleMetric>()

        // Add metrics in the order they appear in currentMetrics
        currentMetrics.forEach { key ->
            allBodyMetrics[key]?.let { metric ->
                orderedBodyMetrics.add(metric.copy(isEnabled = true))
            }
            allOtherMetrics[key]?.let { metric ->
                orderedOtherMetrics.add(metric.copy(isEnabled = true))
            }
        }

        // Add remaining disabled metrics
        allBodyMetrics.values.forEach { metric ->
            if (!orderedBodyMetrics.any { it.key == metric.key }) {
                orderedBodyMetrics.add(metric.copy(isEnabled = false))
            }
        }

        allOtherMetrics.values.forEach { metric ->
            if (!orderedOtherMetrics.any { it.key == metric.key }) {
                orderedOtherMetrics.add(metric.copy(isEnabled = false))
            }
        }

        return Pair(orderedBodyMetrics, orderedOtherMetrics)
    }
}
