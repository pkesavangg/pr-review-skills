package com.greatergoods.meapp.features.common.model

data class ScaleInfo(
    val productName: String,
    val sku: String,
    val imgPath: String?,
    val setupType: String,
    val bodyComp: Boolean
)

val SCALES = listOf(
    ScaleInfo("AppSync Body Fat Scale", "0341", null, "appSync", true),
    ScaleInfo("AppSync Bathroom Scale", "0342", null, "appSync", false),
    ScaleInfo("AppSync Body Fat Scale", "0343", null, "appSync", true),
    ScaleInfo("AppSync Body Fat Scale", "0345", null, "appSync", true),
    ScaleInfo("AppSync Body Fat Scale", "0346", null, "appSync", true),
    ScaleInfo("AppSync Body Fat Scale", "0347", null, "appSync", true),
    ScaleInfo("Basic AppSync Bathroom Scale", "0358", null, "appSync", false),
    ScaleInfo("Basic AppSync Bathroom Scale", "0359", null, "appSync", false),
    ScaleInfo("AppSync Bathroom Scale", "0364", null, "appSync", true),
    ScaleInfo("AppSync Body Fat Scale", "0369", null, "appSync", true),
    ScaleInfo("AppSync Body Fat Scale", "0370", null, "appSync", true),
    ScaleInfo("AppSync Bathroom Scale", "0371", null, "appSync", false),
    ScaleInfo("Bluetooth Smart Scale", "0375", null, "bluetooth", false),
    ScaleInfo("Bluetooth Smart Scale", "0376", null, "bluetooth", false),
    ScaleInfo("Bluetooth Smart Scale", "0378", null, "lcbt", true),
    ScaleInfo("Bluetooth Smart Scale", "0380", null, "bluetooth", false),
    ScaleInfo("Bluetooth Smart Scale", "0382", null, "bluetooth", true),
    ScaleInfo("Bluetooth Scale", "0383", null, "lcbt", true),
    ScaleInfo("Wi-Fi Smart Scale", "0384", null, "espTouchWifi", true),
    ScaleInfo("Wi-Fi Smart Scale", "0385", null, "wifi", true),
    ScaleInfo("Wi-Fi Smart Scale", "0396", null, "wifi", false),
    ScaleInfo("Wi-Fi Smart Scale", "0397", null, "espTouchWifi", false),
    ScaleInfo("AccuCheck Verve Smart Scale", "0412", null, "btWifiR4", true)
) 