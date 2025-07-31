package com.dmdbrands.gurus.weight.domain.model.common

import com.dmdbrands.gurus.weight.features.goal.helper.Weightless

/**
 * Data class representing monthly aggregated history data.
 * Used for displaying monthly statistics and trends.
 */
interface IUnitProcessable<T> {
    fun process(unit: WeightUnit?, weightLess: Weightless?): T
}
