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
 * Converts a database DeviceDetails to a domain model.
 * This is used when you need to pass the data to the UI layer or external components.
 */
fun DeviceDetails.asExternalModel() = Device(
    // Device properties
    id = device.id,
    accountId = device.accountId,
    peripheralIdentifier = device.peripheralIdentifier,
    nickname = device.nickname,
    sku = device.sku,
    mac = device.mac,
    password = device.password,
    isDeleted = device.isDeleted,
    deviceName = device.deviceName,
    deviceType = device.deviceType,
    broadcastId = device.broadcastId,
    broadcastIdString = device.broadcastIdString,
    userNumber = device.userNumber,
    protocolType = device.protocolType,
    createdAt = device.createdAt,
    lastModified = device.lastModified,
    isSynced = device.isSynced,
    isConnected = device.isConnected,
    wifiMac = device.wifiMac,
    isWifiConfigured = device.isWifiConfigured,
    token = device.token,

    // Scale properties
    scaleType = scale?.scaleType,
    bodyComp = scale?.bodyComp ?: false,

    // R4 Preference
    displayName = r4Preference?.displayName,
    displayMetrics = r4Preference?.displayMetrics?.joinToString(","),
    shouldFactoryReset = r4Preference?.shouldFactoryReset ?: false,
    shouldMeasureImpedance = r4Preference?.shouldMeasureImpedance ?: false,
    shouldMeasurePulse = r4Preference?.shouldMeasurePulse ?: false,
    timeFormat = r4Preference?.timeFormat,
    tzOffset = r4Preference?.tzOffset?.toString(),
    wifiFotaScheduleTime = r4Preference?.wifiFotaScheduleTime?.toString(),
    prefsUpdatedAt = r4Preference?.updatedAt,

    // Meta
    modelNumber = meta?.modelNumber,
    serialNumber = meta?.serialNumber,
    firmwareRevision = meta?.firmwareRevision,
    hardwareRevision = meta?.hardwareRevision,
    softwareRevision = meta?.softwareRevision,
    manufacturerName = meta?.manufacturerName,
    systemId = meta?.systemId,
    latestVersion = meta?.latestVersion,

    // BPM
    hasNumericUsers = bpm?.hasNumericUsers
)

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
