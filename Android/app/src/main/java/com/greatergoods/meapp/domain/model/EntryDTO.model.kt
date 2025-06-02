package com.greatergoods.meapp.domain.model

import com.greatergoods.meapp.data.storage.db.entity.BpmEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.EntryEntity
import com.greatergoods.meapp.data.storage.db.entity.WeightScaleEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.ScaleEntryMetricEntity

data class EntryDTO(
    val entry: EntryEntity,
    val bpmEntry: BpmEntryEntity?,
    val scaleEntry: WeightScaleEntryEntity?,
    val scaleEntryMetric: ScaleEntryMetricEntity?
)