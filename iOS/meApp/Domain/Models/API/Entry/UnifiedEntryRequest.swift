//
//  UnifiedEntryRequest.swift
//  meApp
//
//  Request model for the unified `POST /v3/entries/` endpoint (MOB-384).
//

import Foundation

/// Category discriminator for the unified entries endpoint.
///
/// Maps a local `EntryType` onto the wire `category` field used by
/// `POST /v3/entries/`. The write path implemented in MOB-384 covers
/// `weight` and `bp`; `baby` is wired in iOS 3.
enum EntryCategory: String, Codable, Equatable {
    case weight
    case bp
    case baby

    /// Maps the local persistence `EntryType` onto the unified API category.
    init?(entryType: EntryType) {
        switch entryType {
        case .scale: self = .weight
        case .bpm: self = .bp
        case .baby: self = .baby
        }
    }
}

/// A single entry in the unified `POST /v3/entries/` request array.
///
/// The endpoint accepts a raw JSON array of these objects and supports mixed
/// categories in one atomic batch — a single entry is simply an array of one.
/// Optional fields left `nil` are omitted by `JSONEncoder`, so the wire payload
/// carries only the fields relevant to each `category` (the spec's "nulls stripped").
struct UnifiedEntryRequest: Codable, Equatable {
    // MARK: - Common (all categories)
    let category: String
    let operationType: String
    let entryTimestamp: String

    // MARK: - Weight fields
    let weight: Int?
    let bodyFat: Int?
    let muscleMass: Int?
    let water: Int?
    let bmi: Int?
    let boneMass: Int?
    let impedance: Int?
    let unit: String?

    // MARK: - BP fields
    let systolic: Int?
    let diastolic: Int?

    // MARK: - Shared optional
    let pulse: Int?
    let source: String?
    let note: String?

    init(
        category: String,
        operationType: String,
        entryTimestamp: String,
        weight: Int? = nil,
        bodyFat: Int? = nil,
        muscleMass: Int? = nil,
        water: Int? = nil,
        bmi: Int? = nil,
        boneMass: Int? = nil,
        impedance: Int? = nil,
        unit: String? = nil,
        systolic: Int? = nil,
        diastolic: Int? = nil,
        pulse: Int? = nil,
        source: String? = nil,
        note: String? = nil
    ) {
        self.category = category
        self.operationType = operationType
        self.entryTimestamp = entryTimestamp
        self.weight = weight
        self.bodyFat = bodyFat
        self.muscleMass = muscleMass
        self.water = water
        self.bmi = bmi
        self.boneMass = boneMass
        self.impedance = impedance
        self.unit = unit
        self.systolic = systolic
        self.diastolic = diastolic
        self.pulse = pulse
        self.source = source
        self.note = note
    }
}

extension UnifiedEntryRequest {
    /// Builds a unified request entry from a stored operation DTO.
    ///
    /// Returns `nil` for categories outside MOB-384's write-path scope (baby is iOS 3),
    /// letting callers skip unsupported entries without submitting them.
    ///
    /// For `delete` operations only the discriminating fields (`category`,
    /// `operationType`, `entryTimestamp`) are sent — measurement fields are omitted,
    /// since the server identifies the row by timestamp.
    /// - Parameters:
    ///   - dto: The stored operation, as produced by `Entry.toOperationDTO()`.
    ///   - note: The entry note (carried separately because `BathScaleOperationDTO` does not hold it).
    init?(from dto: BathScaleOperationDTO, note: String? = nil) {
        let resolvedType = EntryType(rawValue: dto.entryType ?? EntryType.scale.rawValue) ?? .scale
        guard let category = EntryCategory(entryType: resolvedType), category != .baby else {
            // Baby write path is iOS 3 — out of scope for MOB-384.
            return nil
        }

        let operationType = dto.operationType ?? OperationType.create.rawValue
        let isCreate = operationType == OperationType.create.rawValue
        let timestamp = dto.entryTimestamp ?? ""

        switch category {
        case .weight:
            self.init(
                category: category.rawValue,
                operationType: operationType,
                entryTimestamp: timestamp,
                weight: isCreate ? dto.weight.map { Int($0.rounded()) } : nil,
                bodyFat: isCreate ? dto.bodyFat.map { Int($0.rounded()) } : nil,
                muscleMass: isCreate ? dto.muscleMass.map { Int($0.rounded()) } : nil,
                water: isCreate ? dto.water.map { Int($0.rounded()) } : nil,
                bmi: isCreate ? dto.bmi.map { Int($0.rounded()) } : nil,
                boneMass: isCreate ? dto.boneMass.map { Int($0.rounded()) } : nil,
                impedance: isCreate ? dto.impedance.map { Int($0.rounded()) } : nil,
                unit: isCreate ? dto.unit : nil,
                pulse: isCreate ? dto.pulse.map { Int($0.rounded()) } : nil,
                source: isCreate ? dto.source : nil
            )
        case .bp:
            self.init(
                category: category.rawValue,
                operationType: operationType,
                entryTimestamp: timestamp,
                systolic: isCreate ? dto.systolic.map { Int($0.rounded()) } : nil,
                diastolic: isCreate ? dto.diastolic.map { Int($0.rounded()) } : nil,
                pulse: isCreate ? dto.pulse.map { Int($0.rounded()) } : nil,
                source: isCreate ? dto.source : nil,
                note: isCreate ? note : nil
            )
        case .baby:
            return nil
        }
    }
}
