package com.greatergoods.meapp.domain.model.storage

/**
 * Domain model representing a device in the application.
 * This is a clean model without any database dependencies.
 */
data class Device(
    // Device properties
    val id: String,
    val accountId: String,
    val peripheralIdentifier: String?,
    val nickname: String?,
    val sku: String?,
    val mac: String?,
    val password: String?,
    val isDeleted: Boolean,
    val deviceName: String?,
    val deviceType: String?,
    val broadcastId: String?,
    val broadcastIdString: String?,
    val userNumber: String?,
    val protocolType: String?,
    val createdAt: String?,
    val lastModified: Long?,
    val isSynced: Boolean,
    val isConnected: Boolean,
    val wifiMac: String?,
    val isWifiConfigured: Boolean,
    val token: String?,

    // Body Scale properties
    val scaleType: String?,
    val bodyComp: Boolean,

    // R4 Prefs
    val displayName: String?,
    val displayMetrics: String?,
    val shouldFactoryReset: Boolean,
    val shouldMeasureImpedance: Boolean,
    val shouldMeasurePulse: Boolean,
    val timeFormat: String?,
    val tzOffset: String?,
    val wifiFotaScheduleTime: String?,
    val prefsUpdatedAt: String?,

    // Meta
    val modelNumber: String?,
    val serialNumber: String?,
    val firmwareRevision: String?,
    val hardwareRevision: String?,
    val softwareRevision: String?,
    val manufacturerName: String?,
    val systemId: String?,
    val latestVersion: String?,

    // BPM
    val hasNumericUsers: Boolean?
)
