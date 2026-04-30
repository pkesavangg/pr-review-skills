//
//  AccountFlagRepositoryAPI.swift
//  meApp
//
//  Created by AI Assistant on $(DATE).
//

import Foundation

/// API repository implementation for account flag operations
@MainActor
final class AccountFlagRepositoryAPI: AccountFlagRepositoryAPIProtocol {
    private let httpClient = HTTPClient.shared
    
    private let tag = "AccountFlagRepositoryAPI"
    
    /// Fetches account flags for the current user from the API
    /// - Returns: Array of account flag DTOs
    /// - Throws: HTTPError or AccountFlagError if the operation fails
    func fetchAccountFlags() async throws -> [AccountFlagDTO] {
        do {
            let flags: [AccountFlagDTO] = try await httpClient.get(
                .flags,
                needsAuth: true
            )
            return flags
        } catch {
            LoggerService.shared.log(
                level: .error,
                tag: tag,
                message: "Failed to fetch account flags: \(error.localizedDescription)"
            )
            if let httpError = error as? HTTPError {
                throw AccountFlagError.networkError(httpError)
            }
            throw AccountFlagError.invalidResponse
        }
    }
    
    /// Deletes a specific account flag by ID
    /// - Parameter flagId: The ID of the flag to delete
    /// - Returns: True if deletion was successful (no content response)
    /// - Throws: HTTPError or AccountFlagError if the operation fails
    func deleteAccountFlag(flagId: String) async throws -> Bool {
        do {
            _ = try await httpClient.send(
                .clearFlag(flagId: flagId),
                method: .delete,
                body: EmptyBody(),
                needsAuth: true
            ) as EmptyResponse
            
            LoggerService.shared.log(
                level: .info,
                tag: tag,
                message: "Successfully deleted account flag: \(flagId)"
            )
            return true
        } catch {
            LoggerService.shared.log(
                level: .error,
                tag: tag,
                message: "Failed to delete account flag \(flagId): \(error.localizedDescription)"
            )
            if let httpError = error as? HTTPError {
                throw AccountFlagError.networkError(httpError)
            }
            throw AccountFlagError.deletionFailed(id: flagId)
        }
    }
}
