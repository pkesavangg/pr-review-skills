package com.greatergoods.meapp.domain.model

import com.greatergoods.meapp.data.storage.db.entity.BpmEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.EntryEntity
import com.greatergoods.meapp.data.storage.db.entity.BodyScaleEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.BodyScaleEntryMetricEntity

data class EntryDTO(
    val entry: EntryEntity,
    val bpmEntry: BpmEntryEntity?,
    val scaleEntry: BodyScaleEntryEntity?,
    val scaleEntryMetric: BodyScaleEntryMetricEntity?
)