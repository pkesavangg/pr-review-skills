//
//  BPHistoryMonth.swift
//  meApp
//

import Foundation

/// Represents a monthly summary of blood pressure entries.
struct BPHistoryMonth: Identifiable, Equatable, Hashable {
    /// Unique identifier in "YYYY-MM" format
    let id: String
    /// Number of entries in the month
    let count: Int
    /// Average systolic value for the month
    let avgSystolic: Int
    /// Average diastolic value for the month
    let avgDiastolic: Int
    /// Average pulse for the month
    let avgPulse: Int
    /// Month component (e.g., "06")
    let month: String
    /// Year component (e.g., "2025")
    let year: String
}
