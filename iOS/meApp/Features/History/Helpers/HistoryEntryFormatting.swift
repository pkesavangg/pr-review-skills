//
//  HistoryEntryFormatting.swift
//  meApp
//

import Foundation

// MARK: - BPHistoryEntry

extension BPHistoryEntry {
    var hasNotes: Bool { !(notes ?? "").isEmpty }
    var pressureText: String { "\(systolic)/\(diastolic)" }
}

// MARK: - BabyHistoryEntry

extension BabyHistoryEntry {
    var hasNotes: Bool { !(notes ?? "").isEmpty }

    var weightText: String {
        guard weightLbs > 0 || weightOz > 0 else { return "--" }
        return "\(weightLbs) \(HistoryListStrings.lbs) \(String(format: "%.1f", weightOz)) \(HistoryListStrings.oz)"
    }

    var lengthText: String {
        guard lengthInches > 0 else { return "--" }
        return "\(Int(lengthInches)) \(HistoryListStrings.inUnit)"
    }

    var percentileText: String {
        BabyWeightPercentileCalculator.percentileDisplayText(percentile)
    }
}

// MARK: - BPHistoryMonth

extension BPHistoryMonth {
    var pressureText: String { "\(avgSystolic)/\(avgDiastolic)" }
}
