//
//  DeviceModelType.swift
//  meApp
//
//  Created by Lakshmi Priya on 25/06/25.
//

import Foundation

enum DeviceModelType: String {
    case bluetoothA3 = "BLUETOOTH_A3"
    case bluetoothA6 = "BLUETOOTH_A6"
    case bluetoothR4 = "BLUETOOTH_R4"
    case appsync = "APPSYNC"
    case wifi = "WIFI"
    case babyScale = "BABY_SCALE"
    case bpm = "BPM"

    var displayName: String {
        switch self {
        case .bluetoothA3, .bluetoothA6:
            return "Bluetooth"
        case .bluetoothR4:
            return "Bluetooth/Wi-Fi"
        case .appsync:
            return "Appsync"
        case .wifi:
            return "Wi-Fi"
        case .babyScale:
            return "Bluetooth"
        case .bpm:
            return "Bluetooth"
        }
    }
}

enum DeviceSourceType: String {
  case wifi
  case espTouchWifi
  case bluetooth
  case lcbt
  case lcbtScale = "lcbt scale"
  case bluetoothScale = "bluetooth scale"
  case btWifiR4
  case appsync
  case appsyncScale = "appsync scale"
}
