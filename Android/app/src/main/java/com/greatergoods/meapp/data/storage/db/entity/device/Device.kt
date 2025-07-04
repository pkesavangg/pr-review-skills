package com.greatergoods.meapp.data.storage.db.entity.device

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Room relation class that combines DeviceEntity with its related entities (BodyScale, BPM, Meta, R4Prefs).
 * This makes it easier to fetch related data in a single query.
 *
 * The class uses Room's @Embedded and @Relation annotations to establish relationships:
 * - DeviceEntity is the parent entity
 * - BodyScaleEntity, BpmEntity, DeviceMetaDataEntity, and R4ScalePreferenceEntity are related entities
 * - All relationships are one-to-one, linked by the 'id' field
 */
data class DeviceDetails(
    @Embedded val device: DeviceEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        entity = BodyScaleEntity::class
    )
    val scale: BodyScaleEntity? = null,

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
        entity = R4ScalePreferenceEntity::class,
    )
    val r4Preference: R4ScalePreferenceEntity? = null,
) {
    /**
     * Computed property that returns the appropriate scale data.
     * For R4 scales, returns R4ScalePreferenceEntity, otherwise returns BodyScaleEntity.
     */
    val scaleData: BodyScaleEntity? get() = scale
}
