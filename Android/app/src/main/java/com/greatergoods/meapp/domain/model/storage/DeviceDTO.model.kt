package com.greatergoods.meapp.data.storage.db.entity

import com.greatergoods.meapp.data.storage.db.entity.device.BodyScaleEntity
import com.greatergoods.meapp.data.storage.db.entity.device.BpmEntity
import com.greatergoods.meapp.data.storage.db.entity.device.DeviceEntity
import com.greatergoods.meapp.data.storage.db.entity.device.DeviceMetaDataEntity
import com.greatergoods.meapp.data.storage.db.entity.device.R4ScalePreferenceEntity

/**
 * Data Transfer Object for Device and its related entities.
 * Used to transfer data between layers.
 */
data class DeviceDTO(
    val device: DeviceEntity,
    val scale: BodyScaleEntity? = null,
    val bpm: BpmEntity? = null,
    val meta: DeviceMetaDataEntity? = null,
    val r4Preference: R4ScalePreferenceEntity? = null
)
