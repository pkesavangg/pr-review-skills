//
//  AccountFlag.swift
//  meApp
//
//  Created by AI Assistant on $(DATE).
//

import Foundation

/// Represents an account flag used for triggering app actions like review prompts
public struct AccountFlag: Codable, Identifiable, Equatable {
    /// Unique identifier for the flag
    public let id: String
    
    /// The type of flag (e.g., "app-rate-ask", "scale-review-ask")
    public let type: String
    
    /// The trigger condition (e.g., "login", "entry")
    public let trigger: String
    
    /// Optional metadata associated with the flag
    public let metadata: [String: String]?
    
    /// Timestamp when the flag was created
    public let createdAt: String
    
    /// Account ID this flag belongs to
    public let accountId: String
    
    public init(
        id: String,
        type: String,
        trigger: String,
        metadata: [String: String]? = nil,
        createdAt: String,
        accountId: String
    ) {
        self.id = id
        self.type = type
        self.trigger = trigger
        self.metadata = metadata
        self.createdAt = createdAt
        self.accountId = accountId
    }
}

/// Extension to parse type components for scale review flags
extension AccountFlag {
    /// Returns the base type without additional parameters
    /// For "scale-review-ask WG-WiFi-Scale" returns "scale-review-ask"
    var baseType: String {
        return type.components(separatedBy: " ").first ?? type
    }
    
    /// Returns the parsed flag type as an enum
    var flagType: AccountFlagType? {
        return AccountFlagType(fromTypeString: type)
    }
    
    /// Returns the SKU for scale review flags
    /// For "scale-review-ask WG-WiFi-Scale" returns "WG-WiFi-Scale"
    var scaleSku: String? {
        let components = type.components(separatedBy: " ")
        return components.count > 1 ? components[1] : nil
    }
    
    /// Check if this is an app rate request flag
    var isAppRateFlag: Bool {
        return flagType == .appRateAsk
    }
    
    /// Check if this is a scale review request flag
    var isScaleReviewFlag: Bool {
        return flagType == .scaleReviewAsk
    }
}
