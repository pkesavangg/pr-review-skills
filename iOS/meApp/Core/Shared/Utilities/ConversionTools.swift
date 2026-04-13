// ConversionTools.swift
// Utility class for unit and value conversions (height, weight, BMI, protocol, etc.)
// All methods are static and thread-safe.

import Foundation

final class ConversionTools {
    // MARK: - Height Conversion
    /// Converts stored height (tenths of inches) to formatted string (e.g., 5' 10" or 178 cm)
    static func convertToFormattedHeight(_ height: Int, forceMetric: Bool = false, isMetric: Bool = false) -> String {
        if isMetric || forceMetric {
            return "\(convertStoredHeightToCm(height)) cm"
        } else {
            let feet = convertStoredHeightToFeet(height)
            return "\(feet[0])' \(feet[1])\""
        }
    }
    
    /// Converts stored height (tenths of inches) to [feet, inches]
    static func convertStoredHeightToFeet(_ stored: Int) -> [Int] {
        let inches = Double(stored) / 10.0
        return [Int(inches / 12), Int(inches.truncatingRemainder(dividingBy: 12))]
    }
    
    /// Converts stored height (tenths of inches) to centimeters
    static func convertStoredHeightToCm(_ stored: Int) -> Int {
        return Int(round(Double(stored) * 0.254))
    }
    
    /// Converts centimeters to stored height (tenths of inches)
    static func convertCmToStoredHeight(_ cm: Int) -> Int {
        return Int(round(Double(cm) / 0.254))
    }
    
    /// Converts inches to stored height (tenths of inches)
    static func convertInchesToStoredHeight(_ inches: Int) -> Int {
        return inches * 10
    }
    
    // MARK: - Weight Conversion
    /// Converts stored weight (tenths of lbs) to lbs
    static func convertStoredToLbs(_ stored: Int) -> Double {
        return convertStoredToLbs(Double(stored))
    }

    /// Converts stored weight (tenths of lbs) to lbs
    static func convertStoredToLbs(_ stored: Double) -> Double {
        return stored / 10.0
    }
    
    /// Converts lbs to stored weight (tenths of lbs)
    static func convertLbsToStored(_ lbs: Double) -> Int {
        return Int(round(lbs * 10))
    }
    
    /// Converts stored weight (tenths of lbs) to kg
    static func convertStoredToKg(_ stored: Int) -> Double {
        return convertStoredToKg(Double(stored))
    }

    /// Converts stored weight (tenths of lbs) to kg
    static func convertStoredToKg(_ stored: Double) -> Double {
        return rounded(stored / 22.0462, toPlaces: 1)
    }
    
    /// Converts kg to stored weight (tenths of lbs)
    static func convertKgToStored(_ kgs: Double) -> Int {
        return Int(round(kgs * 2.20462 * 10))
    }
    
    /// Converts display value to stored value (tenths of lbs), metric or imperial
    static func convertDisplayToStored(_ display: Double, forceMetric: Bool = false, isMetric: Bool = false) -> Int {
        if isMetric || forceMetric {
            return convertKgToStored(display)
        } else {
            return convertLbsToStored(display)
        }
    }
    
    /// Converts stored value (tenths of lbs) to display value (kg or lbs)
    static func convertStoredToDisplay(_ stored: Int, isMetric: Bool = false) -> Double {
        return convertStoredToDisplay(Double(stored), isMetric: isMetric)
    }

    /// Converts stored value (tenths of lbs) to display value (kg or lbs)
    static func convertStoredToDisplay(_ stored: Double, isMetric: Bool = false) -> Double {
        if isMetric {
            return convertStoredToKg(stored)
        } else {
            return convertStoredToLbs(stored)
        }
    }
    
    /// Converts Bluetooth scale kg value to stored value (tenths of lbs)
    static func convertBluetoothToStored(_ btKg: Double) -> Double {
        // Bluetooth scales: .2 lbs resolution, special formula
        let intermediate = rounded(btKg * 1.10231, toPlaces: 1)
        return intermediate * 2
    }
    
    /// Appsync display to stored (kg to lbs, more precision)
    static func convertAppsyncDisplayToStored(_ display: Double) -> Int {
        return Int(round(display * 2.20462 * 10))
    }
    
    /// Converts display weight value between units when metric setting changes.
    /// - Parameters:
    ///   - value: The weight value as a string to convert
    ///   - fromMetric: Whether the source value is in metric units
    ///   - toMetric: Whether to convert to metric units
    /// - Returns: Converted weight value as string with one decimal place
    static func convertDisplayWeightValue(_ value: String, fromMetric: Bool, toMetric: Bool) -> String {
        guard !value.isEmpty, fromMetric != toMetric else { return value }
        
        guard let numericValue = Double(value) else { return value }
        
        let convertedValue: Double
        if fromMetric && !toMetric {
            convertedValue = numericValue * 2.20462
        } else if !fromMetric && toMetric {
            convertedValue = numericValue / 2.20462
        } else {
            convertedValue = numericValue
        }
        
        let roundedValue = round(convertedValue * 10.0) / 10.0
        
        // Snap to round number if difference is exactly 0.1 (rounding error from back-and-forth conversion)
        let roundedToInteger = round(roundedValue)
        let difference = abs(roundedValue - roundedToInteger)
        let tolerance: Double = 0.001
        let finalValue = abs(difference - 0.1) < tolerance ? roundedToInteger : roundedValue
        
        return String(format: "%.1f", finalValue)
    }
    
    // MARK: - Baby Weight Conversion
    // All baby weight functions store/read in decigrams (Int).
    // 1 kg = 10,000 decigrams, 1 oz ≈ 283.5 decigrams, 1 lb ≈ 4,535.924 decigrams.

    /// Decigrams per ounce (28.3495 g/oz × 10).
    static let decigramsPerOunce = 283.5
    /// Decigrams per pound (453.5924 g/lb × 10).
    static let decigramsPerPound = 4535.924
    /// Decigrams per kilogram.
    static let decigramsPerKg = 10000.0
    /// Millimeters per inch.
    static let mmPerInch = 25.4

    /// Converts baby weight from kg to decigrams for storage. 1 kg = 10000 decigrams.
    static func convertBabyKgToDecigrams(_ kg: Double) -> Int {
        return Int(round(kg * decigramsPerKg))
    }

    /// Converts baby weight from decigrams to kg for display.
    static func convertBabyDecigramsToKg(_ decigrams: Int) -> Double {
        return rounded(Double(decigrams) / decigramsPerKg, toPlaces: 3)
    }

    /// Converts decigrams to (lbs, oz) tuple for imperial display.
    static func convertBabyDecigramsToLbsOz(_ decigrams: Int) -> (lbs: Int, oz: Double) {
        let totalOz = rounded(Double(decigrams) / decigramsPerOunce, toPlaces: 1)
        let lbs = Int(totalOz / 16.0)
        let oz = lbs > 0 ? rounded(totalOz.truncatingRemainder(dividingBy: 16.0), toPlaces: 1) : totalOz
        return (lbs, oz)
    }

    /// Converts lbs + fractional oz to decigrams for storage.
    static func convertBabyLbsOzToDecigrams(lbs: Int, oz: Double) -> Int {
        let totalOz = Double(lbs) * 16.0 + oz
        return Int(round(totalOz * decigramsPerOunce))
    }

    /// Converts decimal pounds to decigrams for storage.
    static func convertBabyLbToDecigrams(_ lb: Double) -> Int {
        return Int(round(lb * decigramsPerPound))
    }

    /// Converts decigrams to decimal pounds for display.
    static func convertBabyDecigramsToLb(_ decigrams: Int) -> Double {
        return rounded(Double(decigrams) / decigramsPerPound, toPlaces: 3)
    }

    // MARK: - Baby Length Conversion (stored in millimeters)

    /// Converts inches to millimeters for storage.
    static func convertBabyInchesToMm(_ inches: Double) -> Int {
        return Int(round(inches * mmPerInch))
    }

    /// Converts millimeters to inches for display.
    static func convertBabyMmToInches(_ mm: Int) -> Double {
        return rounded(Double(mm) / mmPerInch, toPlaces: 1)
    }

    /// Converts centimeters to millimeters for storage.
    static func convertBabyCmToMm(_ cm: Double) -> Int {
        return Int(round(cm * 10.0))
    }

    /// Converts millimeters to centimeters for display.
    static func convertBabyMmToCm(_ mm: Int) -> Double {
        return Double(mm) / 10.0
    }

    // MARK: - Baby Scale Display Graduation
    // Baby scales (0220/0222) apply graduation-based rounding that matches the scale LCD.
    // These functions convert raw decigrams to display values with correct resolution.

    /// Weight thresholds in grams for graduation adjustment.
    static let graduationThreshold18LbGrams = 8165.0
    static let graduationThreshold25LbGrams = 11340.0
    /// Precise grams-to-pounds factor used by the 0222 scale.
    static let gramsToPoundsConversionFactor = 2.204623
    /// 0222 manufacturer calibration numerator for lbs/oz conversion.
    static let calibration0222Numerator = 369874.0
    /// 0222 manufacturer calibration denominator for lbs/oz conversion (2^20).
    static let calibration0222Denominator = 1048576.0

    /// Unit selector for baby display graduation routing.
    enum BabyDisplayUnit {
        case kg
        case lbDecimal
        case lbOz
    }

    /// Routes raw decigrams through the correct graduation function for the given scale source.
    /// Returns graduated decigrams suitable for formatting. If source is nil or not a baby scale,
    /// returns the input unchanged.
    /// When `isBabyScaleEntry` is true and source is nil (legacy entries), defaults to 0220 graduation.
    static func convertToDisplayWeightBase(decigrams: Int, source: String?, unit: BabyDisplayUnit, isBabyScaleEntry: Bool = false) -> Int {
        let effectiveSource: String? = source ?? (isBabyScaleEntry ? "0220" : nil)
        guard let effectiveSource = effectiveSource,
              effectiveSource.contains("0220") || effectiveSource.contains("0222") else {
            return decigrams
        }
        let source = effectiveSource
        switch unit {
        case .kg:
            // Both 0220 and 0222 share the same kg graduation logic (5g/10g/50g bands)
            let scaleKg = convert0220DecigramsToKg(decigrams)
            return Int(round(Double(convertBabyKgToDecigrams(scaleKg))))
        case .lbDecimal:
            let scaleLb: Double
            if source.contains("0222") {
                scaleLb = convert0222DecigramsToLbDecimal(decigrams)
            } else {
                scaleLb = convert0220DecigramsToLbDecimal(decigrams)
            }
            return Int(round(Double(scaleLb) * decigramsPerPound))
        case .lbOz:
            let scaleLbOz: (lbs: Int, oz: Double)
            if source.contains("0222") {
                scaleLbOz = convert0222DecigramsToLbOz(decigrams)
            } else {
                scaleLbOz = convert0220DecigramsToLbOz(decigrams)
            }
            return convertBabyLbsOzToDecigrams(lbs: scaleLbOz.lbs, oz: scaleLbOz.oz)
        }
    }

    // MARK: 0220 Scale Conversions

    /// Converts 0220 scale decigrams to kg with graduation rounding (5g/10g/50g).
    static func convert0220DecigramsToKg(_ decigrams: Int) -> Double {
        let grams = Double(decigrams) / 10.0
        if grams >= graduationThreshold25LbGrams {
            return (Double(Int(round(grams / 50.0))) * 50.0) / 1000.0
        } else if grams >= graduationThreshold18LbGrams {
            return Double(Int(round(grams / 10.0))) / 100.0
        } else {
            return (Double(Int(round(grams / 5.0))) * 5.0) / 1000.0
        }
    }

    /// Converts 0220 scale decigrams to decimal pounds with graduation rounding.
    static func convert0220DecigramsToLbDecimal(_ decigrams: Int) -> Double {
        let grams = Double(decigrams) / 10.0
        let unroundedLbs = (grams / 1000.0) * 2.2046
        if grams >= graduationThreshold25LbGrams {
            return rounded(unroundedLbs, toPlaces: 1)
        } else if grams >= graduationThreshold18LbGrams {
            return rounded(unroundedLbs / 2.0, toPlaces: 2) * 2.0
        } else {
            return rounded(unroundedLbs, toPlaces: 2)
        }
    }

    /// Converts 0220 scale decigrams to lbs/oz with graduation rounding.
    static func convert0220DecigramsToLbOz(_ decigrams: Int) -> (lbs: Int, oz: Double) {
        let grams = Double(decigrams) / 10.0
        let totalOz: Double
        if grams >= graduationThreshold25LbGrams {
            totalOz = Double(Int(round((grams / 2.0) / 28.35))) * 2.0
        } else if grams >= graduationThreshold18LbGrams {
            totalOz = Double(Int(round((grams * 5.0) / 28.35))) / 5.0
        } else {
            totalOz = rounded(grams / 28.35, toPlaces: 1)
        }
        let lbs = Int(totalOz / 16.0)
        let oz = lbs > 0 ? rounded(totalOz.truncatingRemainder(dividingBy: 16.0), toPlaces: 1) : rounded(totalOz, toPlaces: 1)
        return (lbs, oz)
    }

    // MARK: 0222 Scale Conversions

    /// Converts 0222 scale decigrams to decimal pounds with graduation rounding.
    /// Uses the precise 2.204623 conversion factor per manufacturer spec.
    static func convert0222DecigramsToLbDecimal(_ decigrams: Int) -> Double {
        let grams = Double(decigrams) / 10.0
        let unroundedLbs = (grams / 1000.0) * gramsToPoundsConversionFactor
        if grams >= graduationThreshold25LbGrams {
            return rounded(unroundedLbs, toPlaces: 1)
        } else if grams >= graduationThreshold18LbGrams {
            return rounded(unroundedLbs / 2.0, toPlaces: 2) * 2.0
        } else {
            return rounded(unroundedLbs, toPlaces: 2)
        }
    }

    /// Converts 0222 scale decigrams to lbs/oz using the manufacturer's calibration factor (369874/1048576).
    static func convert0222DecigramsToLbOz(_ decigrams: Int) -> (lbs: Int, oz: Double) {
        let transmissionWeight = Double(decigrams) / 10.0
        let converted = Int(round(transmissionWeight * calibration0222Numerator / calibration0222Denominator))
        let lb = Int(floor(Double(converted) / 160.0))
        let rawOz = converted - (lb * 160)

        var indexing = 5
        if transmissionWeight >= graduationThreshold25LbGrams {
            indexing = 50
        } else if transmissionWeight >= graduationThreshold18LbGrams {
            indexing = 10
        }

        let adjustedOz: Double
        switch indexing {
        case 10:
            adjustedOz = rounded(Double(Int(round(Double(rawOz) / 2.0)) * 2) / 10.0, toPlaces: 1)
        case 50:
            adjustedOz = rounded(Double(Int(round(Double(rawOz) / 20.0)) * 20) / 10.0, toPlaces: 1)
        default:
            adjustedOz = rounded(Double(rawOz) / 10.0, toPlaces: 1)
        }

        return (lb, adjustedOz)
    }

    // MARK: - BMI
    /// Calculates BMI from weight (tenths of lbs) and height (tenths of inches)
    /// Returns 0 if height is zero to avoid division by zero.
    static func calculateBMI(weight: Double, height: Int) -> Int {
        guard height != 0 else { return 0 }
        // BMI = (weight / height / height) * 100000
        return Int(round((Double(weight) / Double(height) / Double(height)) * 100000))
    }
    
    // MARK: - Value Parsing
    /// Parses a value from string to number/bool/null/undefined/NaN/Infinity
    static func parseValue(_ value: String) -> Any? {
        if let boolVal = Bool(value) { return boolVal }
        if value == "null" { return nil }
        if value == "undefined" { return nil }
        if value == "NaN" { return Double.nan }
        if value == "Infinity" { return Double.infinity }
        if value == "-Infinity" { return -Double.infinity }
        if let num = Double(value) { return num }
        return value
    }
    
    // MARK: - Internal helpers
    /// Rounds a Double to the given number of decimal places
    static func rounded(_ value: Double, toPlaces places: Int) -> Double {
        let divisor = pow(10.0, Double(places))
        return (value * divisor).rounded() / divisor
    }
    
    // MARK: - Height Picker Helpers (shared)
    /// Picker column options for imperial height selection (feet & inches).
    static let heightInchesOptions: [[String]] = [
        (2...7).map { "\($0)" },  // Feet
        (0...11).map { "\($0)" }  // Inches
    ]

    /// Picker column options for metric height selection (centimetres 100-299).
    static let heightCmOptions: [[String]] = [
        (1...2).map { "\($0)" },  // Hundreds
        (0...9).map { "\($0)" },  // Tens
        (0...9).map { "\($0)" }   // Ones
    ]

    /// Returns picker default selections (feet/in & cm arrays) given stored tenths-inch height.
    static func pickerSelections(from storedHeight: Int) -> (inches: [String], cm: [String]) {
        let feetInches = convertStoredHeightToFeet(storedHeight)
        // Clamp feet to valid range (2-7)
        let clampedFeet = max(2, min(7, feetInches[0]))
        let clampedInches = max(0, min(11, feetInches[1]))
        let inchesSel = ["\(clampedFeet)", "\(clampedInches)"]

        let cm = convertStoredHeightToCm(storedHeight)
        // Clamp cm to valid range (100-299)
        let clampedCm = max(100, min(299, cm))
        let cmString = String(format: "%03d", clampedCm)
        let cmSel = cmString.map { String($0) }
        return (inchesSel, cmSel)
    }
    
    // MARK: - Height Validation
    
    // Validates height in feet/inches (2'0" to 7'11")
    static func isValidHeightInches(feet: Int, inches: Int) -> Bool {
        guard feet >= 2 && feet <= 7 else { return false }
        guard inches >= 0 && inches <= 11 else { return false }
        let totalInches = (feet * 12) + inches
        return totalInches >= 24 && totalInches <= 95
    }
    
    // Validates height in cm (100-299)
    static func isValidHeightCm(_ cm: Int) -> Bool {
        return cm >= 100 && cm <= 299
    }
    
    // Validates height picker values
    static func isValidHeightPickerValues(fromMetric: Bool, values: [String]) -> Bool {
        if fromMetric {
            guard values.count >= 3 else { return false }
            let cm = Int(values.joined()) ?? 0
            return isValidHeightCm(cm)
        } else {
            guard values.count >= 2 else { return false }
            let feet = Int(values[0]) ?? 0
            let inches = Int(values[1]) ?? 0
            return isValidHeightInches(feet: feet, inches: inches)
        }
    }
}
