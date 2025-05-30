package com.greatergoods.meapp.data.storage.db.entity

import androidx.room.Embedded
import androidx.room.Relation
import com.greatergoods.meapp.domain.model.Device

/**
 * Room relation class that combines DeviceEntity with its related entities (Scale, BPM, Meta, R4Prefs).
 * This makes it easier to fetch related data in a single query.
 *
 * The class uses Room's @Embedded and @Relation annotations to establish relationships:
 * - DeviceEntity is the parent entity
 * - ScaleEntity, BpmEntity, DeviceMetaDataEntity, and R4ScalePreferenceEntity are related entities
 * - All relationships are one-to-one, linked by the 'id' field
 */
data class DeviceDetails(
    @Embedded val device: DeviceEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        entity = ScaleEntity::class
    )
    val scale: ScaleEntity? = null,

    @Relation(
        parentColumn = "id",
        entityColumn = "id"
    )
    val bpm: BpmEntity? = null,

    @Relation(
        parentColumn = "id",
        entityColumn = "id"
    )
    val meta: DeviceMetaDataEntity? = null,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        entity = R4ScalePreferenceEntity::class
    )
    val r4Preference: R4ScalePreferenceEntity? = null
) {
    /**
     * Computed property that returns the appropriate scale data.
     * For R4 scales, returns R4ScalePreferenceEntity, otherwise returns ScaleEntity.
     */
    val scaleData: ScaleEntity? get() = scale

    /**
     * Converts this DeviceDetails to a DTO for database operations.
     */
    fun toDTO() = DeviceDTO(
        device = device,
        scale = scale,
        bpm = bpm,
        meta = meta,
        r4Preference = r4Preference
    )
}

/**
 * Creates a DeviceDetails from a DTO.
 */
fun fromDTO(dto: DeviceDTO) = DeviceDetails(
    device = dto.device,
    scale = dto.scale,
    bpm = dto.bpm,
    meta = dto.meta,
    r4Preference = dto.r4Preference
)
