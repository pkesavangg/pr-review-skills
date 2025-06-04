package com.greatergoods.meapp.domain.model.common

data class DeviceInfo(
    val appVersion: String,
    val deviceManufacturer: String,
    val deviceOSName: String,
    val deviceOSVersion: String,
    val deviceUUID: String,
    val deviceModel: String,
    val fcmToken: String?,
)
