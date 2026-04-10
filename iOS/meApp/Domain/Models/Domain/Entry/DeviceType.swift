//
//  DeviceType.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 18/06/25.
//

import Foundation
/// Represents the type of device that recorded the entry.
enum DeviceType: String, Codable, Equatable, CaseIterable {
    case scale
    case bpm
    case babyScale

    /// Derives the device type from a SKU code by checking the BPM and baby scale catalogs.
    static func fromSku(_ sku: String?) -> DeviceType {
        guard let sku, !sku.isEmpty else { return .scale }

        if bpmSkus.contains(sku) {
            return .bpm
        }

        let lookupSku = DeviceHelper.mapSkuForDisplay(sku)
        if let scaleInfo = SCALES.first(where: { $0.sku == lookupSku }),
           scaleInfo.setupType == .babyScale {
            return .babyScale
        }

        return .scale
    }
}
