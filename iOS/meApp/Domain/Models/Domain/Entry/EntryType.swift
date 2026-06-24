//
//  EntryType.swift
//  meApp
//

import Foundation

/// Discriminator for the type of measurement an entry represents.
///
/// - scale: Adult weight scale entry
/// - bpm: Blood Pressure Monitor entry
/// - baby: Baby scale entry
enum EntryType: String, Codable, Equatable, CaseIterable {
    case scale
    case bpm
    case baby
}
