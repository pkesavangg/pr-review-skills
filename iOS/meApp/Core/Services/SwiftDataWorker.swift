//
//  SwiftDataWorker.swift
//  meApp
//
//  Thread-safe SwiftData background worker using @ModelActor.
//  Ensures all SwiftData relationship access happens within the actor's isolated context,
//  preventing cross-context crashes.
//

import Foundation
import SwiftData

// MARK: - Data Transfer Types

/// Value type for transferring entry data across actor boundaries.
/// Contains all extracted data from Entry and its relationships (BathScaleEntry, BathScaleMetric).
struct EntryData: Sendable {
    let id: PersistentIdentifier
    let entryId: UUID
    let accountId: String
    let entryTimestamp: String
    let serverTimestamp: String?
    let operationType: String
    let entryType: String
    let isSynced: Bool

    // Extracted from BathScaleEntry relationship
    let weight: Int?
    let bodyFat: Int?
    let muscleMass: Int?
    let water: Int?
    let bmi: Int?
    let source: String?

    // Extracted from BathScaleMetric relationship
    let bmr: Int?
    let metabolicAge: Int?
    let proteinPercent: Int?
    let pulse: Int?
    let skeletalMusclePercent: Int?
    let subcutaneousFatPercent: Int?
    let visceralFatLevel: Int?
    let boneMass: Int?
    let impedance: Int?
    let unit: String?

    /// Converts to BathScaleOperationDTO for API/transfer use
    func toDTO() -> BathScaleOperationDTO {
        BathScaleOperationDTO(
            accountId: accountId,
            bmr: bmr.map { Double($0) },
            bmi: bmi.map { Double($0) },
            bodyFat: bodyFat.map { Double($0) },
            boneMass: boneMass.map { Double($0) },
            entryTimestamp: entryTimestamp,
            entryType: nil,
            impedance: impedance.map { Double($0) },
            metabolicAge: metabolicAge.map { Double($0) },
            muscleMass: muscleMass.map { Double($0) },
            operationType: operationType,
            proteinPercent: proteinPercent.map { Double($0) },
            pulse: pulse.map { Double($0) },
            serverTimestamp: serverTimestamp,
            skeletalMusclePercent: skeletalMusclePercent.map { Double($0) },
            source: source,
            subcutaneousFatPercent: subcutaneousFatPercent.map { Double($0) },
            systolic: nil,
            diastolic: nil,
            meanArterial: nil,
            unit: unit,
            visceralFatLevel: visceralFatLevel.map { Double($0) },
            water: water.map { Double($0) },
            weight: weight.map { Double($0) }
        )
    }
}

/// Result of fetching progress data - contains all data needed for progress calculations.
struct ProgressFetchResult: Sendable {
    let latestEntry: EntryData?
    let weekEntries: [EntryData]
    let monthEntries: [EntryData]
    let allEntries: [EntryData]
    let totalCount: Int

    /// First entry ever recorded (oldest)
    var firstEntry: EntryData? {
        allEntries.last
    }

    /// Week start entry (oldest in week range)
    var weekStartEntry: EntryData? {
        weekEntries.last
    }

    /// Month start entry (oldest in month range)
    var monthStartEntry: EntryData? {
        monthEntries.last
    }
}

/// Value type for device data transfer
struct DeviceData: Sendable {
    let id: PersistentIdentifier
    let deviceId: String
    let accountId: String
    let nickname: String?
    let deviceType: String?
    let protocolType: String?
    let isConnected: Bool
    let isWifiConfigured: Bool

    // R4ScalePreference data
    let preferenceId: PersistentIdentifier?
    let displayName: String?
    let shouldMeasureImpedance: Bool
    let shouldMeasurePulse: Bool
    let displayMetrics: [String]
    let isSynced: Bool
}

// MARK: - SwiftData Worker Actor

/// Background worker for SwiftData operations using @ModelActor.
/// Ensures thread-safe access to SwiftData models and their relationships.
///
/// Usage:
/// ```swift
/// let worker = SwiftDataWorker(modelContainer: PersistenceController.shared.container)
/// let result = try await worker.fetchProgressData(accountId: "user123")
/// // result contains all extracted data - safe to use across actors
/// ```
@ModelActor
actor SwiftDataWorker {

    // MARK: - Entry Fetching

    /// Fetches all entry data for progress calculation with relationships extracted.
    /// - Parameter accountId: The account ID to fetch entries for
    /// - Returns: ProgressFetchResult containing all extracted entry data
    func fetchProgressData(accountId: String) async throws -> ProgressFetchResult {
        let operationType = "create"
        let calendar = Calendar.current
        let now = Date()
        
        // Calculate date ranges for week and month (date-based filtering like BEFORE commit)
        guard let weekStartDate = calendar.date(byAdding: .day, value: -7, to: now),
              let monthStartDate = calendar.date(byAdding: .day, value: -30, to: now) else {
            throw NSError(domain: "SwiftDataWorker", code: 500, userInfo: [NSLocalizedDescriptionKey: "Failed to calculate date ranges"])
        }
        let isoFormatter = DateTimeTools.isoFormatter(useUTC: true)
        let nowString = isoFormatter.string(from: now)
        let weekStartString = isoFormatter.string(from: weekStartDate)
        let monthStartString = isoFormatter.string(from: monthStartDate)
        
        // Fetch once ordered DESC (newest first), then derive the week/month slices
        // in memory. This avoids repeating nearly identical SQLite work three times
        // during a single dashboard progress refresh.
        let allDescriptor = FetchDescriptor<Entry>(
            predicate: #Predicate<Entry> { entry in
                entry.accountId == accountId && entry.operationType == operationType
            },
            sortBy: [SortDescriptor(\Entry.entryTimestamp, order: .reverse)]
        )
        let allEntries = try modelContext.fetch(allDescriptor)
        let allEntryData = allEntries.map { extractEntryData($0) }

        // Entry timestamps are stored in ISO-8601 UTC format, so lexical comparison
        // preserves ordering without reparsing every timestamp during streak refreshes.
        let weekEntryData = allEntryData.filter { entry in
            entry.entryTimestamp >= weekStartString && entry.entryTimestamp <= nowString
        }

        let monthEntryData = allEntryData.filter { entry in
            entry.entryTimestamp >= monthStartString && entry.entryTimestamp <= nowString
        }

        return ProgressFetchResult(
            latestEntry: allEntryData.first,
            weekEntries: weekEntryData,
            monthEntries: monthEntryData,
            allEntries: allEntryData,
            totalCount: allEntries.count
        )
    }

    /// Fetches entries as DTOs for a given account and operation type.
    /// - Parameters:
    ///   - accountId: The account ID
    ///   - operationType: The operation type filter (e.g., "create", "delete")
    /// - Returns: Array of BathScaleOperationDTO
    func fetchEntriesAsDTO(accountId: String, operationType: String) async throws -> [BathScaleOperationDTO] {
        let descriptor = FetchDescriptor<Entry>(
            predicate: #Predicate<Entry> { entry in
                entry.accountId == accountId && entry.operationType == operationType
            },
            sortBy: [SortDescriptor(\Entry.entryTimestamp, order: .reverse)]
        )

        let entries = try modelContext.fetch(descriptor)
        return entries.map { extractEntryData($0).toDTO() }
    }

    /// Fetches only entry identifiers for later MainActor refetch.
    /// Use this when you need to modify entries - refetch on MainActor using the identifiers.
    /// - Parameters:
    ///   - accountId: The account ID
    ///   - operationType: The operation type filter
    /// - Returns: Array of PersistentIdentifier
    func fetchEntryIdentifiers(accountId: String, operationType: String) async throws -> [PersistentIdentifier] {
        let descriptor = FetchDescriptor<Entry>(
            predicate: #Predicate<Entry> { entry in
                entry.accountId == accountId && entry.operationType == operationType
            },
            sortBy: [SortDescriptor(\Entry.entryTimestamp, order: .reverse)]
        )

        let entries = try modelContext.fetch(descriptor)
        return entries.map { $0.persistentModelID }
    }

    /// Fetches a single entry by UUID and returns its data.
    /// - Parameter id: The entry UUID
    /// - Returns: EntryData or nil if not found
    func fetchEntryData(byId id: UUID) async throws -> EntryData? {
        let descriptor = FetchDescriptor<Entry>(
            predicate: #Predicate<Entry> { $0.id == id }
        )

        guard let entry = try modelContext.fetch(descriptor).first else {
            return nil
        }

        return extractEntryData(entry)
    }

    // MARK: - Device Fetching

    /// Fetches device data including R4ScalePreference for a given device ID.
    /// - Parameter deviceId: The device ID string
    /// - Returns: DeviceData or nil if not found
    func fetchDeviceData(deviceId: String) async throws -> DeviceData? {
        let descriptor = FetchDescriptor<Device>(
            predicate: #Predicate<Device> { $0.id == deviceId }
        )

        guard let device = try modelContext.fetch(descriptor).first else {
            return nil
        }

        return extractDeviceData(device)
    }

    /// Fetches all devices for an account with their preference data extracted.
    /// - Parameter accountId: The account ID
    /// - Returns: Array of DeviceData
    func fetchDevicesData(accountId: String) async throws -> [DeviceData] {
        let descriptor = FetchDescriptor<Device>(
            predicate: #Predicate<Device> { $0.accountId == accountId }
        )

        let devices = try modelContext.fetch(descriptor)
        return devices.map { extractDeviceData($0) }
    }

    // MARK: - Private Extraction Methods

    /// Extracts all data from Entry including relationships - safe within ModelActor
    private func extractEntryData(_ entry: Entry) -> EntryData {
        EntryData(
            id: entry.persistentModelID,
            entryId: entry.id,
            accountId: entry.accountId,
            entryTimestamp: entry.entryTimestamp,
            serverTimestamp: entry.serverTimestamp,
            operationType: entry.operationType,
            entryType: entry.entryType,
            isSynced: entry.isSynced,
            // BathScaleEntry relationship - safe to access here
            weight: entry.scaleEntry?.weight,
            bodyFat: entry.scaleEntry?.bodyFat,
            muscleMass: entry.scaleEntry?.muscleMass,
            water: entry.scaleEntry?.water,
            bmi: entry.scaleEntry?.bmi,
            source: entry.scaleEntry?.source,
            // BathScaleMetric relationship - safe to access here
            bmr: entry.scaleEntryMetric?.bmr,
            metabolicAge: entry.scaleEntryMetric?.metabolicAge,
            proteinPercent: entry.scaleEntryMetric?.proteinPercent,
            pulse: entry.scaleEntryMetric?.pulse,
            skeletalMusclePercent: entry.scaleEntryMetric?.skeletalMusclePercent,
            subcutaneousFatPercent: entry.scaleEntryMetric?.subcutaneousFatPercent,
            visceralFatLevel: entry.scaleEntryMetric?.visceralFatLevel,
            boneMass: entry.scaleEntryMetric?.boneMass,
            impedance: entry.scaleEntryMetric?.impedance,
            unit: entry.scaleEntryMetric?.unit
        )
    }

    /// Extracts all data from Device including R4ScalePreference - safe within ModelActor
    private func extractDeviceData(_ device: Device) -> DeviceData {
        let preference = device.r4ScalePreference

        return DeviceData(
            id: device.persistentModelID,
            deviceId: device.id,
            accountId: device.accountId,
            nickname: device.nickname,
            deviceType: device.deviceType,
            protocolType: device.protocolType,
            isConnected: device.isConnected ?? false,
            isWifiConfigured: device.isWifiConfigured ?? false,
            // R4ScalePreference relationship - safe to access here
            preferenceId: preference?.persistentModelID,
            displayName: preference?.displayName,
            shouldMeasureImpedance: preference?.shouldMeasureImpedance ?? false,
            shouldMeasurePulse: preference?.shouldMeasurePulse ?? false,
            displayMetrics: preference?.displayMetrics ?? [],
            isSynced: preference?.isSynced ?? false
        )
    }
}
