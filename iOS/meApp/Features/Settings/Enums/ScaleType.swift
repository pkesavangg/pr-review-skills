//
//  ScaleType.swift
//  meApp
//
//  Created by Lakshmi Priya on 25/06/25.
//

import Foundation

enum ScaleType: String {
    case bluetoothA3 = "BLUETOOTH_A3"
    case bluetoothA6 = "BLUETOOTH_A6"
    case bluetoothR4 = "BLUETOOTH_R4"
    case appsync = "APPSYNC"
    case wifi = "WIFI"
}


enum ScaleSourceType: String {
  case wifi = "wifi"
  case espTouchWifi = "espTouchWifi"
  case bluetooth = "bluetooth"
  case lcbt = "lcbt"
  case lcbtScale = "lcbt scale"
  case bluetoothScale = "bluetooth scale"
  case btWifiR4 = "btWifiR4"
  case appsync = "appsync"
  case appsyncScale = "appsync scale"

  var rawValue: String {
    switch self {
    case .wifi: return "wifi"
    case .espTouchWifi: return "espTouchWifi"
    case .bluetooth: return "bluetooth"
    case .lcbt: return "lcbt"
    case .lcbtScale: return "lcbt scale"
    case .bluetoothScale: return "bluetooth scale"
    case .btWifiR4: return "btWifiR4"
    case .appsync: return "appsync"
    case .appsyncScale: return "appsync scale"
    }
  }

  init(rawValue: String) {
    self = ScaleSourceType(rawValue: rawValue) ?? .bluetooth
  }
}
