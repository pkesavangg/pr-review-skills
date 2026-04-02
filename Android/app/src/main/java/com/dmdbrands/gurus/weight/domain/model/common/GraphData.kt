package com.dmdbrands.gurus.weight.domain.model.common

import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBabySummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBpmSummary

/**
 * Sealed return type for graph data across all product types.
 * Service wraps product-specific data into the correct variant.
 * Used by the single-product dashboard view which switches via ProductSelection.
 */
sealed interface GraphData {
    data class Weight(val data: List<PeriodBodyScaleSummary>) : GraphData
    data class BloodPressure(val data: List<PeriodBpmSummary>) : GraphData
    data class Baby(val data: List<PeriodBabySummary>) : GraphData
}
