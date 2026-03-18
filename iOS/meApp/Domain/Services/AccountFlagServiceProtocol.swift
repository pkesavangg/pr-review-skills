//
//  AccountFlagServiceProtocol.swift
//  meApp
//
//  Created by AI Assistant on $(DATE).
//

import Foundation

/// Protocol defining the interface for account flag operations
@MainActor
public protocol AccountFlagServiceProtocol: AppReviewHandlerProtocol, ScaleReviewHandlerProtocol {
    /// Fetches account flags from the API
    /// Prefers flags with trigger 'login' if present, otherwise returns the first flag
    /// - Returns: The preferred account flag or nil if none found
    /// - Throws: AccountFlagError if the operation fails
    func getAccountFlag() async throws -> AccountFlag?
    
    /// Checks if the provided trigger matches the current flag and handles it accordingly
    /// For app-rate-ask flags, triggers the native app review prompt
    /// For scale-review-ask flags, emits a scale review event
    /// - Parameter trigger: The trigger type to check ("login" or "entry")
    /// - Returns: True if a flag was found and processed, false otherwise
    /// - Throws: AccountFlagError if the operation fails
    func checkAccountFlag(trigger: String) async throws -> Bool
    
    /// Deletes a specific account flag by ID
    /// - Parameter flagId: The ID of the flag to delete
    /// - Returns: True if deletion was successful, false otherwise
    /// - Throws: AccountFlagError if the operation fails
    func deleteFlag(flagId: String) async throws -> Bool
}

/// Protocol for handling app review actions triggered by account flags
public protocol AppReviewHandlerProtocol {
    /// Triggers the native app store review prompt
    /// - Parameter isFromDebug: Whether this is triggered from debug menu (affects timing)
    func triggerAppReview(isFromDebug: Bool) async
}

/// Protocol for handling scale review actions triggered by account flags
@MainActor
public protocol ScaleReviewHandlerProtocol {
    /// Emits a scale review event with the provided parameters
    /// - Parameters:
    ///   - screen: The screen identifier for the review
    ///   - sku: The scale SKU
    ///   - flagId: The original flag ID
    func emitScaleReview(screen: String, sku: String, flagId: String)
}
