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
        config.allowWholeNumbers ? "0" : "0.0"
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
            return "0.0"
        }
        
        // Extract only digits
        let digitsOnly = input.replacingOccurrences(of: "[^0-9]", with: "", options: .regularExpression)
        
        // Handle empty digits or just "0"
        if digitsOnly.isEmpty || digitsOnly == "0" {
            return "0.0"
        }
        
        // Remove leading zeros
        let trimmedDigits = digitsOnly.replacingOccurrences(of: "^0+", with: "", options: .regularExpression)
        
        if trimmedDigits.isEmpty {
            return "0.0"
        }
        
        // Limit to maxLength digits
        let limitedDigits = String(trimmedDigits.prefix(config.maxLength))
        let length = limitedDigits.count
        
        switch length {
        case 1:
            return "0.\(limitedDigits)"
        default:
            let beforeDecimal = limitedDigits.dropLast()
            let afterDecimal = limitedDigits.suffix(1)
            return "\(beforeDecimal).\(afterDecimal)"
        }
    }
    
    private func formatWholeNumber(_ input: String) -> String {
        let digitsOnly = input.replacingOccurrences(of: "[^0-9]", with: "", options: .regularExpression)
        
        if digitsOnly.isEmpty {
            return emptyValue
        }
        
        let trimmedDigits = digitsOnly.replacingOccurrences(of: "^0+", with: "", options: .regularExpression)
        
        if trimmedDigits.isEmpty {
            return "0"
        }
        
        let limitedDigits = String(trimmedDigits.prefix(config.maxLength))
        return limitedDigits
    }
}