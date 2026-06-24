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

    /// Server-facing `deviceType` value used by the unified `/paired-device/` and legacy
    /// `/paired-scale/` Me App 2.0 endpoints. The server uses snake-case identifiers that
    /// differ from this enum's local raw values.
    var serverValue: String {
        switch self {
        case .scale: return "weight_scale"
        case .babyScale: return "baby_scale"
        case .bpm: return "bpm"
        }
    }

    /// Maps a server-provided `deviceType` (`weight_scale`/`baby_scale`/`bpm`) back to the
    /// local enum. Returns `nil` for unknown/absent values so callers can fall back to
    /// SKU-based derivation.
    static func fromServerValue(_ value: String?) -> DeviceType? {
        switch value?.lowercased() {
        case "weight_scale": return .scale
        case "baby_scale": return .babyScale
        case "bpm": return .bpm
        default: return nil
        }
    }
}
