//
//  AccountFlagRepositoryAPIProtocol.swift
//  meApp
//
//  Created by AI Assistant on $(DATE).
//

import Foundation

/// Protocol defining the API repository interface for account flag operations
public protocol AccountFlagRepositoryAPIProtocol {
    /// Fetches account flags for the current user from the API
    /// - Returns: Array of account flag DTOs
    /// - Throws: HTTPError or AccountFlagError if the operation fails
    func fetchAccountFlags() async throws -> [AccountFlagDTO]
    
    /// Deletes a specific account flag by ID
    /// - Parameter flagId: The ID of the flag to delete
    /// - Returns: True if deletion was successful (no content response)
    /// - Throws: HTTPError or AccountFlagError if the operation fails
    func deleteAccountFlag(flagId: String) async throws -> Bool
}
