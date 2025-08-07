package com.dmdbrands.gurus.weight.features.common.helper

import com.dmdbrands.library.ggbluetooth.model.GGDeviceDetail

object DeviceHelper {
  fun GGDeviceDetail.getSKU() = SKU_MAP[deviceName] ?: "0375"

  private val SKU_MAP = mapOf(
    "MY_SCALE" to "0480",
    "1490BT1" to "0604",
    "10376B" to "0376",
    "0376B" to "0376",
    "376B" to "0376",
    "0202B" to "0375",
    "1202B" to "0375",
    "202B" to "0375",
    "11251B" to "0380",
    "1251B" to "0380",
    "01251B" to "0380",
    "1270B" to "0380",
    "11270B" to "0380",
    "01270B" to "0380",
    "gG BS 0412" to "0412",
    "LS212-B" to "0383",
    "gG-RPM 0022" to "0383",
    "gG PulseOx 0003" to "0003",
    "gG-RPM 0040" to "0604",
    "gG BGM 0005" to "0005",
    "0062" to "0062",
    "gG BPM 0603" to "0603",
    "gG BS 0222" to "0222",
    "gG BS 0220" to "0220",
    "BS1711-B" to "0220",
    "Smart Blood Pressure Monitor" to "0663",
    "gG BPM 0667" to "0661",
    "gG BPM 0634" to "0634",
    "gG BS 0351" to "0375",
    "gG BS 0344" to "0344",
  )
}
