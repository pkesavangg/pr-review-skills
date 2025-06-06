package com.greatergoods.meapp.domain.model.storage

import com.greatergoods.meapp.data.storage.db.entity.entry.BpmEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.EntryEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryMetricEntity

data class EntryDTO(
    val entry: EntryEntity,
    val bpmEntry: BpmEntryEntity?,
    val scaleEntry: BodyScaleEntryEntity?,
    val scaleEntryMetric: BodyScaleEntryMetricEntity?
)
