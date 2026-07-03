package com.dmdbrands.gurus.weight.features.DeviceSetup.manager

import com.dmdbrands.library.ggbluetooth.model.GGBTUser

interface IDevicePairingManager {
    fun connectToBluetooth()
    fun replaceAccount(userName: String?)
    fun deleteUser(user: GGBTUser)
    fun showRestoreAccountAlert()
    fun cancelTimeout()
}
