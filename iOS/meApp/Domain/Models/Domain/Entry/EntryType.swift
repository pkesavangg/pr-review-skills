//
//  EntryType.swift
//  meApp
//

import Foundation

/// Discriminator for the type of measurement an entry represents.
///
/// - wg: Weight / Scale entry
/// - bpm: Blood Pressure Monitor entry
///
/// Distinct from `DeviceType` which represents hardware:
/// - A baby scale has `deviceType == .babyScale` but `entryType == .wg`
/// - A BPM device has `deviceType == .bpm` and `entryType == .bpm`
enum EntryType: String, Codable, Equatable, CaseIterable {
    case wg
    case bpm
}
