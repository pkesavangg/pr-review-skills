//
//  AccountFlagDTO.swift
//  meApp
//
//  Created by AI Assistant on $(DATE).
//

import Foundation

/// Data Transfer Object for account flags from the API
public struct AccountFlagDTO: Codable {
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
    
    enum CodingKeys: String, CodingKey {
        case id
        case type
        case trigger
        case metadata
        case createdAt = "created_at"
        case accountId = "account_id"
    }
}

/// Extension to convert DTO to domain model
extension AccountFlagDTO {
    /// Converts the DTO to a domain AccountFlag model
    func toDomainModel() -> AccountFlag {
        return AccountFlag(
            id: id,
            type: type,
            trigger: trigger,
            metadata: metadata,
            createdAt: createdAt,
            accountId: accountId
        )
    }
}
