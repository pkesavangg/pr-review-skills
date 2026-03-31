//
//  MetricFieldFormatter.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 10/06/25.
//

import Foundation
// MARK: - Metric Field Formatter

class MetricFieldFormatter: ObservableObject {
    private let config: TextInputConfig
    
    init(config: TextInputConfig) {
        self.config = config
    }
    
    var initialValue: String {
        ""
    }
    
    var emptyValue: String {
        ""
    }
    
    func formatInput(_ input: String) -> String {
        // Handle empty input case specially
        if input.isEmpty {
            return emptyValue
        }
        
        if config.allowWholeNumbers {
            return formatWholeNumber(input)
        } else {
            return formatDecimalNumber(input)
        }
    }
    
    func isValidValue(_ value: String) -> Bool {
        // Empty string is considered valid
        if value.isEmpty {
            return true
        }
        
        guard let numValue = Double(value),
              let maxVal = config.maxValue else {
            return true
        }
        return numValue <= maxVal
    }
    
    func shouldUpdateValue(from oldValue: String, to newValue: String) -> Bool {
        // Allow empty values
        if newValue.isEmpty {
            return true
        }
        
        let formatted = formatInput(newValue)
        return isValidValue(formatted)
    }
    
    // MARK: - Private Formatting Methods
    
    private func formatDecimalNumber(_ input: String) -> String {
        // Handle empty or invalid input
        if input.isEmpty {
            return emptyValue
        }
        
        if input == "." {
            return emptyValue
        }
        
        // Extract only digits
        let digitsOnly = input.replacingOccurrences(of: "[^0-9]", with: "", options: .regularExpression)
        
        // Allow single zero
        let trimmedDigits = digitsOnly.replacingOccurrences(of: "^0+", with: "", options: .regularExpression)
        let digits: String
        if trimmedDigits.isEmpty && digitsOnly.contains("0") {
            // When clearZeroValue is enabled, treat zero-only input as empty
            if config.clearZeroValue {
                return emptyValue
            }
            digits = "0"
        } else {
            digits = trimmedDigits
        }

        if digits.isEmpty {
            return emptyValue
        }
        
        // Limit to maxLength digits
        let decimalPlaces = config.decimalPlaces
        let limitedDigits = String(digits.prefix(config.maxLength))
        let length = limitedDigits.count

        if length <= decimalPlaces {
            let padded = String(repeating: "0", count: decimalPlaces - length) + limitedDigits
            return "0.\(padded)"
        } else {
            let splitIndex = length - decimalPlaces
            let integerPart = String(limitedDigits.prefix(splitIndex))
            let decimalPart = String(limitedDigits.suffix(decimalPlaces))
            return "\(integerPart).\(decimalPart)"
        }
    }
    
    private func formatWholeNumber(_ input: String) -> String {
        // Extract only numeric characters
        let digitsOnly = input.replacingOccurrences(of: "[^0-9]", with: "", options: .regularExpression)
        
        // If nothing numeric, return empty (cleared value)
        guard !digitsOnly.isEmpty else {
            return emptyValue
        }
        
        // Decide whether to preserve leading zeros based on configuration
        let normalizedDigits: String
        if config.showPrefixZero {
            // Keep the original digits (will still be length-clamped below)
            normalizedDigits = digitsOnly
        } else {
            // Trim leading zeros but keep a single zero when the string is all zeros
            let trimmed = digitsOnly.replacingOccurrences(of: "^0+", with: "", options: .regularExpression)
            normalizedDigits = trimmed.isEmpty ? "0" : trimmed
        }
        
        // Enforce maxLength constraint
        let limitedDigits = String(normalizedDigits.prefix(config.maxLength))
        return limitedDigits
    }
}
