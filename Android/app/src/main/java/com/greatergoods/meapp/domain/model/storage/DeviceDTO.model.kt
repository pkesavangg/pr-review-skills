package com.greatergoods.meapp.data.storage.db.entity

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
