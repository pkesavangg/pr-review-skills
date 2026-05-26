import Foundation

/// Sendable value-type view of an `Entry` for safe cross-actor handoff.
///
/// `Entry` is a SwiftData `@Model` and is bound to the `ModelContext` that
/// produced it. When that context goes out of scope (e.g. after a
/// `performBackgroundTask` closure returns), accessing the model — even reading
/// a primitive property — can fault inside SwiftData's container-internal
/// tracking dictionaries (`__RawDictionaryStorage.find`).  Mutating it from
/// another isolation domain compounds the corruption.  See MA-3898.
///
/// Use a snapshot when the caller needs to read entry fields across `await`
/// boundaries or pass them to another actor.
struct EntrySnapshot: Sendable, Equatable {
    let id: UUID
    let accountId: String
    let entryTimestamp: String
    let serverTimestamp: String?
    let operationType: String
    let deviceType: String
    let isSynced: Bool
    let isFailedToSync: Bool
    let attempts: Int
    let weight: Int?
    let bodyFat: Int?
    let bmi: Int?
    let pulse: Int?
}

extension EntrySnapshot {
    /// Builds a snapshot from a SwiftData `Entry`. Must be called from the same
    /// isolation domain that owns the entry's `ModelContext` (i.e. inside the
    /// background-task closure that produced it).
    init(from entry: Entry) {
        self.id = entry.id
        self.accountId = entry.accountId
        self.entryTimestamp = entry.entryTimestamp
        self.serverTimestamp = entry.serverTimestamp
        self.operationType = entry.operationType
        self.deviceType = entry.deviceType
        self.isSynced = entry.isSynced
        self.isFailedToSync = entry.isFailedToSync
        self.attempts = entry.attempts
        self.weight = entry.scaleEntry?.weight
        self.bodyFat = entry.scaleEntry?.bodyFat
        self.bmi = entry.scaleEntry?.bmi
        self.pulse = entry.scaleEntryMetric?.pulse
    }
}
