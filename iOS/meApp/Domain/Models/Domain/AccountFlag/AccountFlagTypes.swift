//
//  AccountFlagTypes.swift
//  meApp
//
//  Created by AI Assistant on $(DATE).
//

import Foundation

// MARK: - Account Flag Type Enum

/// Enumeration of supported account flag types
public enum AccountFlagType: String, CaseIterable {
    /// App rate request flag type
    case appRateAsk = "app-rate-ask"
    
    /// Scale review request flag type
    case scaleReviewAsk = "scale-review-ask"
    
    /// Initialize from a flag type string, extracting just the base type
    /// For example, "scale-review-ask WG-WiFi-Scale" becomes .scaleReviewAsk
    /// - Parameter typeString: The full type string from the account flag
    public init?(fromTypeString typeString: String) {
        let baseType = typeString.components(separatedBy: " ").first ?? typeString
        self.init(rawValue: baseType)
    }
}

// MARK: - Account Flag Error Enum

/// Errors that can occur when working with account flags
public enum AccountFlagError: Error, LocalizedError {
    /// No flags found for the account
    case noFlagsFound
    
    /// Flag not found with the specified ID
    case flagNotFound(id: String)
    
    /// Network error when fetching or deleting flags
    case networkError(Error)
    
    /// API returned an unexpected response
    case invalidResponse
    
    /// Failed to delete the flag
    case deletionFailed(id: String)
    
    /// Invalid flag data received from API
    case invalidFlagData
    
    public var errorDescription: String? {
        switch self {
        case .noFlagsFound:
            return "No account flags found"
        case .flagNotFound(let id):
            return "Account flag not found with ID: \(id)"
        case .networkError(let error):
            return "Network error: \(error.localizedDescription)"
        case .invalidResponse:
            return "Invalid response from account flags API"
        case .deletionFailed(let id):
            return "Failed to delete account flag with ID: \(id)"
        case .invalidFlagData:
            return "Invalid account flag data received"
        }
    }
}
