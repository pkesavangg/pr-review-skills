//
//  BabyProfileDetail.swift
//  meApp
//

import Foundation

/// One label/value pair rendered in an expanded "My Kids" baby row (MOB-1605).
/// `label` doubles as the stable identity since the four rows are fixed and unique.
struct BabyProfileDetail: Identifiable, Equatable {
    var id: String { label }
    let label: String
    let value: String
}

/// Formats a `Baby`'s stored birth metrics for display, respecting the account's
/// `measurementUnits`. The `Baby` model stores length in inches and weight as a
/// split lbs + oz pair (imperial is the source of truth); this converts to the
/// preferred unit at display time only. Pure/static so it is unit-testable without DI.
enum BabyProfileDisplayFormatter {
    private static let empty = MyKidsStrings.Details.empty

    /// e.g. "June 10, 2024". Returns "--" when no birthday is recorded.
    static func birthday(_ date: Date?) -> String {
        guard let date else { return empty }
        return DateTimeTools.formatter("MMMM d, yyyy").string(from: date)
    }

    /// Maps the stored raw sex ("male"/"female"/"private", or a legacy capitalized value)
    /// to a display string ("Male"/"Female"/"Private"). Returns "--" when unset.
    static func biologicalSex(_ rawValue: String?) -> String {
        guard let sex = Sex(rawInput: rawValue) else { return empty }
        return sex.rawValue.capitalized
    }

    /// e.g. "25.8 in" (imperial) or "65.5 cm" (metric). Returns "--" when no length is recorded.
    static func birthLength(inches: Double?, units: MeasurementUnits) -> String {
        guard let inches, inches > 0 else { return empty }
        switch units {
        case .metric:
            let cm = ConversionTools.convertBabyMmToCm(ConversionTools.convertBabyInchesToMm(inches))
            return "\(trimmed(cm)) \(BabyScaleSetupStrings.BabyProfile.cmUnit)"
        case .imperialLbOz, .imperialLbDecimal:
            return "\(trimmed(inches)) \(BabyScaleSetupStrings.BabyProfile.inUnit)"
        }
    }

    /// e.g. "16 lb 8 oz" (lb-oz), "16.5 lb" (lb-decimal) or "7.48 kg" (metric).
    /// Returns "--" when no weight is recorded.
    static func birthWeight(lbs: Double?, oz: Double?, units: MeasurementUnits) -> String {
        let pounds = lbs ?? 0
        let ounces = oz ?? 0
        guard pounds > 0 || ounces > 0 else { return empty }

        switch units {
        case .imperialLbOz:
            return "\(Int(pounds)) \(BabyScaleSetupStrings.BabyProfile.lbsUnit) "
                + "\(trimmed(ounces)) \(BabyScaleSetupStrings.BabyProfile.ozUnit)"
        case .imperialLbDecimal:
            let totalLbs = pounds + ounces / 16.0
            return "\(String(format: "%.1f", totalLbs)) \(BabyScaleSetupStrings.BabyProfile.lbsUnit)"
        case .metric:
            let decigrams = ConversionTools.convertBabyLbsOzToDecigrams(lbs: Int(pounds), oz: ounces)
            let kg = ConversionTools.convertBabyDecigramsToKg(decigrams)
            return "\(String(format: "%.2f", kg)) \(BabyScaleSetupStrings.BabyProfile.kgUnit)"
        }
    }

    /// Formats a measurement to one decimal, dropping a trailing ".0" so whole values
    /// read as "8 oz" / "25 in" rather than "8.0 oz" (matches the Figma spec).
    private static func trimmed(_ value: Double) -> String {
        let rounded = (value * 10).rounded() / 10
        return rounded == rounded.rounded()
            ? String(Int(rounded))
            : String(format: "%.1f", rounded)
    }
}
