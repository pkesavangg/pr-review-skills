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
    let isReconnect: Bool
    let isDuplicated: Bool
    
    init(sku: String, scale: Device, event: DeviceDiscoveryEvent?, isReconnect: Bool = false, isDuplicated: Bool = false) {
        self.sku = sku
        self.scale = scale
        self.event = event
        self.isReconnect = isReconnect
        self.isDuplicated = isDuplicated
    }
}
