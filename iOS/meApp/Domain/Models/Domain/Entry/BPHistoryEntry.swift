//
//  BPHistoryEntry.swift
//  meApp
//

import Foundation

/// Represents a single blood pressure entry in history.
struct BPHistoryEntry: Identifiable, Equatable {
    let id: UUID
    /// ISO8601 timestamp string
    let entryTimestamp: String
    let systolic: Int
    let diastolic: Int
    let pulse: Int
    let notes: String?
}
