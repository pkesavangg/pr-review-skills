package com.dmdbrands.gurus.weight.domain.model.common

import com.dmdbrands.gurus.weight.domain.model.storage.entry.BabyEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BpmEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry

/**
 * Sealed return type for grouped history (totals view).
 * Service wraps product-specific data into the correct variant.
 */
sealed interface GroupedHistory {
    data class Weight(val months: List<HistoryMonth>) : GroupedHistory
    data class BloodPressure(val months: List<BpHistoryMonth>) : GroupedHistory
    data class Baby(val weeks: List<BabyWeekGroup>) : GroupedHistory
}

/**
 * Sealed return type for history detail (drill-down view).
 * Service wraps product-specific entries into the correct variant.
 */
sealed interface HistoryDetail {
    data class Weight(val entries: List<ScaleEntry>) : HistoryDetail
    data class BloodPressure(val entries: List<BpmEntry>) : HistoryDetail
    data class Baby(val entries: List<BabyEntry>) : HistoryDetail
}
