//
//  AccountFlagService.swift
//  meApp
//
//  Created by AI Assistant on $(DATE).
//

import Combine
import Foundation

/// Service responsible for managing account flags and triggering appropriate actions
@MainActor
final class AccountFlagService: AccountFlagServiceProtocol, ObservableObject {
    static let shared = AccountFlagService()
    
    @Injector private var logger: LoggerService
    @Injector private var appReviewService: AppReviewService
    
    private let apiRepo: AccountFlagRepositoryAPIProtocol = AccountFlagRepositoryAPI()
    private let tag = "AccountFlagService"
    
    /// Currently stored flag (cached after fetching)
    @Published private var currentFlag: AccountFlag?
    
    /// Scale review event publisher for scale review flags
    let scaleReviewSubject = PassthroughSubject<ScaleReviewEvent, Never>()
    
    private init() {}
    
    /// Fetches account flags from the API
    /// Prefers flags with trigger 'login' if present, otherwise returns the first flag
    /// - Returns: The preferred account flag or nil if none found
    /// - Throws: AccountFlagError if the operation fails
    func getAccountFlag() async throws -> AccountFlag? {
        do {
            logger.log(
                level: .info,
                tag: tag,
                message: "Fetching account flags from API"
            )
            
            let flagDTOs = try await apiRepo.fetchAccountFlags()
            
            guard !flagDTOs.isEmpty else {
                logger.log(
                    level: .info,
                    tag: tag,
                    message: "No account flags found"
                )
                currentFlag = nil
                return nil
            }
            
            // Convert DTOs to domain models
            let flags = flagDTOs.map { $0.toDomainModel() }
            
            // Prefer login flags over entry flags
            let preferredFlag = flags.first { $0.trigger == "login" } ?? flags.first
            
            currentFlag = preferredFlag
            
            logger.log(
                level: .info,
                tag: tag,
                message: "Found account flag: type=\(preferredFlag?.type ?? "nil"), trigger=\(preferredFlag?.trigger ?? "nil"), id=\(preferredFlag?.id ?? "nil")"
            )
            
            return preferredFlag
        } catch {
            logger.log(
                level: .error,
                tag: tag,
                message: "Failed to fetch account flags: \(error.localizedDescription)"
            )
            currentFlag = nil
            throw error
        }
    }
    
    /// Checks if the provided trigger matches the current flag and handles it accordingly
    /// For app-rate-ask flags, triggers the native app review prompt
    /// For scale-review-ask flags, emits a scale review event
    /// - Parameter trigger: The trigger type to check ("login" or "entry")
    /// - Returns: True if a flag was found and processed, false otherwise
    /// - Throws: AccountFlagError if the operation fails
    func checkAccountFlag(trigger: String) async throws -> Bool {
        guard let flag = currentFlag else {
            logger.log(
                level: .debug,
                tag: tag,
                message: "No current flag to check for trigger: \(trigger)"
            )
            return false
        }
        
        // Check if the trigger matches
        guard flag.trigger == trigger else {
            logger.log(
                level: .debug,
                tag: tag,
                message: "Flag trigger '\(flag.trigger)' does not match requested trigger '\(trigger)'"
            )
            return false
        }
        
        logger.log(
            level: .info,
            tag: tag,
            message: "Processing account flag: type=\(flag.baseType), trigger=\(trigger), id=\(flag.id)"
        )
        
        // Handle different flag types using enum
        guard let flagType = flag.flagType else {
            logger.log(
                level: .debug,
                tag: tag,
                message: "Unknown flag type: \(flag.baseType)"
            )
            return false
        }
        
        switch flagType {
        case .appRateAsk:
            return try await handleAppRateFlag(flag)
            
        case .scaleReviewAsk:
            return try await handleScaleReviewFlag(flag)
        }
    }
    
    /// Deletes a specific account flag by ID
    /// - Parameter flagId: The ID of the flag to delete
    /// - Returns: True if deletion was successful, false otherwise
    /// - Throws: AccountFlagError if the operation fails
    func deleteFlag(flagId: String) async throws -> Bool {
        do {
            logger.log(
                level: .info,
                tag: tag,
                message: "Deleting account flag: \(flagId)"
            )
            
            let success = try await apiRepo.deleteAccountFlag(flagId: flagId)
            
            if success {
                // Clear the current flag if it was the one we just deleted
                if currentFlag?.id == flagId {
                    currentFlag = nil
                }
                
                logger.log(
                    level: .info,
                    tag: tag,
                    message: "Successfully deleted account flag: \(flagId)"
                )
            }
            
            return success
        } catch {
            logger.log(
                level: .error,
                tag: tag,
                message: "Failed to delete account flag \(flagId): \(error.localizedDescription)"
            )
            throw error
        }
    }
    
    // MARK: - Private Methods
    
    /// Handles app rate flags by deleting the flag and triggering the review
    private func handleAppRateFlag(_ flag: AccountFlag) async throws -> Bool {
        // Delete the flag first
        let wasDeleted = try await deleteFlag(flagId: flag.id)
        
        guard wasDeleted else {
            logger.log(
                level: .error,
                tag: tag,
                message: "Failed to delete app rate flag: \(flag.id)"
            )
            return false
        }
        
        // Trigger the app review
        await appReviewService.triggerAppReview(isFromDebug: false)
        
        logger.log(
            level: .info,
            tag: tag,
            message: "App rate flag processed successfully: \(flag.id)"
        )
        
        return true
    }
    
    /// Handles scale review flags by deleting the flag and emitting a scale review event
    private func handleScaleReviewFlag(_ flag: AccountFlag) async throws -> Bool {
        // Delete the flag first
        let wasDeleted = try await deleteFlag(flagId: flag.id)
        
        guard wasDeleted else {
            logger.log(
                level: .error,
                tag: tag,
                message: "Failed to delete scale review flag: \(flag.id)"
            )
            return false
        }
        
        // Extract the SKU from the flag type
        let sku = flag.scaleSku ?? ""
        
        // Emit the scale review event
        let event = ScaleReviewEvent(
            screen: "scaleReview",
            sku: sku,
            flagId: flag.id
        )
        
        scaleReviewSubject.send(event)
        
        logger.log(
            level: .info,
            tag: tag,
            message: "Scale review flag processed successfully: \(flag.id), sku: \(sku)"
        )
        
        return true
    }
}
