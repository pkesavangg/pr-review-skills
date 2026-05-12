package com.dmdbrands.gurus.weight.domain.model.storage.entry

import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit

data class WeightSnapshotPoint(
    override val period: String,
    override val entryTimestamp: String,
    val weight: Double,
    val unit: WeightUnit,
) : PeriodSummary {
    override fun getTimeStamp(): Long = DateTimeConverter.isoToTimestamp(entryTimestamp)
}
