package com.greatergoods.meapp.domain.model.storage

import com.greatergoods.meapp.data.storage.db.entity.BodyScaleEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.BodyScaleEntryMetricEntity
import com.greatergoods.meapp.data.storage.db.entity.BpmEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.EntryEntity

data class EntryDTO(
    val entry: EntryEntity,
    val bpmEntry: BpmEntryEntity?,
    val scaleEntry: BodyScaleEntryEntity?,
    val scaleEntryMetric: BodyScaleEntryMetricEntity?
)
