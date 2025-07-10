//
//  ScaleDiscoverSheetInfo.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 10/07/25.
//
import Foundation

struct ScaleDiscoverSheetInfo: Identifiable {
    let id = UUID()
    let sku: String
    let scale: Device
    let event: DeviceDiscoveryEvent?
}
