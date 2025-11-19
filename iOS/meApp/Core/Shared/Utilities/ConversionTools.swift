// ConversionTools.swift
// Utility class for unit and value conversions (height, weight, BMI, protocol, etc.)
// Migrated from Angular conversion-tools.service.ts
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
        return Double(stored) / 10.0
    }
    
    /// Converts lbs to stored weight (tenths of lbs)
    static func convertLbsToStored(_ lbs: Double) -> Int {
        return Int(round(lbs * 10))
    }
    
    /// Converts stored weight (tenths of lbs) to kg
    static func convertStoredToKg(_ stored: Int) -> Double {
        return rounded(Double(stored) / 22.046, toPlaces: 1)
    }
    
    /// Converts kg to stored weight (tenths of lbs)
    static func convertKgToStored(_ kgs: Double) -> Int {
        return Int(round(kgs * 2.2046 * 10))
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
        if isMetric {
            return convertStoredToKg(stored)
        } else {
            return convertStoredToLbs(stored)
        }
    }
    
    /// Converts Bluetooth scale kg value to stored value (tenths of lbs)
    static func convertBluetoothToStored(_ btKg: Double) -> Double {
        // Bluetooth scales: .2 lbs resolution, special formula
        return Double(rounded(btKg * 1.1023 * 2 * 10, toPlaces: 0) / 10)
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
        let inchesSel = ["\(feetInches[0])", "\(feetInches[1])"]

        let cm = convertStoredHeightToCm(storedHeight)
        let cmString = String(format: "%03d", cm)
        let cmSel = cmString.map { String($0) }
        return (inchesSel, cmSel)
    }
}
