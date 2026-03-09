package com.dmdbrands.gurus.weight.features.ScaleSetup.manager

import com.dmdbrands.library.ggbluetooth.model.GGBTUser

interface IScalePairingManager {
    fun connectToBluetooth()
    fun replaceAccount(userName: String?)
    fun deleteUser(user: GGBTUser)
    fun showRestoreAccountAlert()
    fun cancelTimeout()
}
