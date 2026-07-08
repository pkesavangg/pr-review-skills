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
    /// Raw entry source (e.g. "manual", "bluetooth monitor"). Drives edit permissions (MOB-1172).
    var source: String?

    /// True when the reading was typed in manually (values editable); false for a device-synced
    /// reading, where only the note may be edited.
    var isManual: Bool { EntrySource.isManualEntry(source) }
}
