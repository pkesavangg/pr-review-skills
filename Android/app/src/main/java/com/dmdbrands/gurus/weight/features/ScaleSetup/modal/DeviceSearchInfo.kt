package com.dmdbrands.gurus.weight.features.ScaleSetup.modal

import com.dmdbrands.gurus.weight.domain.model.storage.Device

data class DeviceSearchInfo(
    val isMonitorExists: Boolean = false,
    val isMonitorExistsWithSameUser: Boolean = false,
    val isMonitorExistsWithDifferentUser: Boolean = false,
    val deviceInfo: Device? = null,
)
