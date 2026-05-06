//
//  IntegrationsService.swift
//  meApp
//
//  Created by Lakshmi Priya on 03/06/25.
//

import Foundation
import SwiftData
import Combine

@MainActor
final class IntegrationsService: IntegrationServiceProtocol {
    static let shared = IntegrationsService()
    
    @Injector var accountService: AccountService
    @Injector var logger: LoggerService
    @Injector var entryService: EntryService
    
    
    // MARK: - Combine
    /// Holds Combine cancellables for the lifetime of the service.
    private var cancellables = Set<AnyCancellable>()
    
    // MARK: - Properties
    private let apiRepository = IntegrationAPIRepository()
    private let localRepository = IntegrationRepository()
    private let tag = "IntegrationService"

    
    // MARK: - Initializer -------------------------------------------------
    /// Subscribes to `EntryService.entrySaved` so that every newly-created entry
    /// is automatically forwarded to the HealthKit log endpoint (if the account
    /// is integrated) without `EntryService` needing to know about integrations.
    init() {
        // Listen to new entries and forward to HealthKit when required.
        // Uses EntryNotification (Sendable) to safely receive data across actor boundaries.
        entryService.entrySaved
            .sink { [weak self] notification in
                // Fire-and-forget so the publisher chain is never blocked.
                Task { await self?.logHealthEntry(notification: notification) }
            }
            .store(in: &cancellables)
    }
    
    // MARK: - Helper
    @Sendable
    private func getAccountId() async throws -> String {
        await MainActor.run {
            accountService.activeAccount?.accountId ?? ""
        }
    }
    
    // MARK: - IntegrationServiceProtocol Implementation
    func getIntegrationUrl(_ provider: IntegrationType) async throws -> String {
        let accountId = try await getAccountId()
        let pathMap: [IntegrationType: String] = [
            .fitbit: "fitbit",
            .google: "google-fit",
            .myFitnessPal: "mfp",
            .underArmour: "ua",
            .healthConnect: "health-connect",
            .healthKit: "health-kit"
        ]
        guard let path = pathMap[provider] else {
            throw NSError(domain: "Invalid provider", code: 0)
        }
        return "\(API.baseURL)/\(path)/\(accountId)"
    }
    
    func removeIntegration(_ provider: IntegrationType) async throws {
        let accountId = try await getAccountId()
        logger.log(level: .info, tag: tag, message: "Remove integration requested. provider=\(provider.rawValue), accountId=\(accountId)")
        do {
            try await apiRepository.removeIntegration(accountId: accountId, provider: provider)
            logger.log(level: .info, tag: tag, message: "Removed integration from API. provider=\(provider.rawValue), accountId=\(accountId)")
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to remove integration from API. provider=\(provider.rawValue), accountId=\(accountId), error=\(error.localizedDescription)")
            throw error
        }
        try localRepository.setIntegrationData(accountId: accountId, info: nil)
        logger.log(level: .success, tag: tag, message: "Cleared local integration data. provider=\(provider.rawValue), accountId=\(accountId)")
    }
    
    func getStoredIntegrationData() async throws -> IntegrationInfo? {
        let accountId = try await getAccountId()
        return try localRepository.getIntegrationData(accountId: accountId)
    }
    
    func setStoredIntegrationData(_ info: IntegrationInfo?) async throws {
        let accountId = try await getAccountId()
        logger.log(level: .info, tag: tag, message: "Set stored integration data requested. provider=\(info?.type.rawValue ?? "none"), isIntegrated=\(info?.isIntegrated ?? false), accountId=\(accountId)")
        try localRepository.setIntegrationData(accountId: accountId, info: info)
        if let integrationType = info?.type {
            do {
                try await accountService.updateIntegrations(integrationType: integrationType)
            } catch {
                logger.log(level: .error, tag: tag, message: "Failed to update account integrations. provider=\(integrationType.rawValue), accountId=\(accountId), error=\(error.localizedDescription)")
            }
        }
        
        logger.log(level: .success, tag: tag, message: "Set stored integration data completed. provider=\(info?.type.rawValue ?? "none"), isIntegrated=\(info?.isIntegrated ?? false), accountId=\(accountId)")
    }
    
    func isIntegrationAlreadyUsed(type: IntegrationType) async throws -> Bool {
        let accountId = try await getAccountId()
        return try localRepository.isIntegrationAlreadyUsed(accountId: accountId, type: type)
    }
    
    func clearIntegrationStatus(integrationType: IntegrationType) async throws {
        let accountId = try await getAccountId()
        logger.log(level: .info, tag: tag, message: "Clear integration status requested. provider=\(integrationType.rawValue), accountId=\(accountId)")
        let integrationInfo = IntegrationInfo(
            type: integrationType,
            isIntegrated: false
        )
        do {
            try await self.setStoredIntegrationData(integrationInfo)
            try await accountService.deleteHealthIntegration(integrationType)
            logger.log(level: .success, tag: tag, message: "Clear integration status completed. provider=\(integrationType.rawValue), accountId=\(accountId)")
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed clearing integration status. provider=\(integrationType.rawValue), accountId=\(accountId), error=\(error.localizedDescription)")
        }
    }
    
    // MARK: - Entry Sync Operations ------------------------------------------------

    /// Syncs a new entry to the integrated health service (e.g., HealthKit) if integration is active.
    /// This method checks if HealthKit integration is active and delegates to the appropriate service.
    func syncNewEntry(_ entry: Entry) async throws {
        // Create notification to safely pass data across actor boundaries, then delegate.
        try await syncNewEntry(notification: EntryNotification(from: entry))
    }

    /// Syncs a new entry to the integrated health service using an EntryNotification.
    /// Used by paths where the source is not a MainActor-bound `Entry` (e.g., remote merge).
    func syncNewEntry(notification: EntryNotification) async throws {
        guard let integrationInfo = try await getStoredIntegrationData(),
              integrationInfo.isIntegrated else {
            // No integration active, nothing to sync
            return
        }

        switch integrationInfo.type {
        case .healthKit:
            try await HealthKitService.shared.syncNewData(notification: notification)
            logger.log(level: .success, tag: tag, message: "Synced new entry to HealthKit. timestamp=\(notification.entryTimestamp)")
        default:
            // Other integrations not implemented for entry sync yet
            logger.log(level: .debug, tag: tag, message: "Entry sync not implemented for integration type: \(integrationInfo.type.rawValue)")
        }
    }

    /// Deletes an entry from the integrated health service (e.g., HealthKit) if integration is active.
    /// This method checks if HealthKit integration is active and delegates to the appropriate service.
    func deleteEntry(_ entry: Entry) async throws {
        guard let integrationInfo = try await getStoredIntegrationData(),
              integrationInfo.isIntegrated else {
            // No integration active, nothing to delete
            return
        }

        // Create notification to safely pass data across actor boundaries
        let notification = EntryNotification(from: entry)

        switch integrationInfo.type {
        case .healthKit:
            // Delegate to HealthKit service for deletion using notification (safe cross-actor)
            let success = try await HealthKitService.shared.deleteEntry(notification: notification)
            if success {
                logger.log(level: .success, tag: tag, message: "Deleted entry from HealthKit. entryId=\(entry.id)")
            } else {
                logger.log(level: .error, tag: tag, message: "Failed deleting entry from HealthKit. entryId=\(entry.id)")
            }
        default:
            // Other integrations not implemented for entry deletion yet
            logger.log(level: .debug, tag: tag, message: "Entry deletion not implemented for integration type: \(integrationInfo.type.rawValue)")
        }
    }
    
    // MARK: - Account Management Operations ------------------------------------------------
    
    /// Clears all integration data if integration is active (used during account deletion).
    /// This method checks if integration is active and delegates to the appropriate service.
    func clearIntegration() async throws {
        guard let integrationInfo = try await getStoredIntegrationData(),
              integrationInfo.isIntegrated else {
            // No integration active, nothing to clear
            logger.log(level: .debug, tag: tag, message: "No integration found, skipping clear operation")
            return
        }
        
        logger.log(level: .info, tag: tag, message: "Clear integration data requested. provider=\(integrationInfo.type.rawValue)")
        switch integrationInfo.type {
        case .healthKit:
            // Delegate to HealthKit service for clearing all data
            try await HealthKitService.shared.clearHealthKit()
            logger.log(level: .success, tag: tag, message: "Cleared HealthKit data during account deletion")
        default:
            // Other integrations not implemented for data clearing yet
            logger.log(level: .debug, tag: tag, message: "Data clearing not implemented for integration type: \(integrationInfo.type.rawValue)")
        }
    }
    
    // MARK: - Health Integration Logging ------------------------------------------------
    /// Sends the newly-created entry data to the `/integrations/health/log` endpoint when the
    /// current account is integrated with Apple Health and at least one permission is granted.
    ///
    /// Errors from the network layer are swallowed – only a log entry is written so that
    /// the caller (e.g. `EntryService`) never fails because of this background task.
    /// - Parameter notification: The EntryNotification containing extracted entry data.
    func logHealthEntry(notification: EntryNotification) async {
        do {
            // Ensure the account has the HealthKit integration enabled
            guard let integrationInfo = try await getStoredIntegrationData(),
                  integrationInfo.type == .healthKit,
                  integrationInfo.isIntegrated else {
                return
            }

            // Ensure at least one HealthKit permission is currently approved
            let approvedPermissions = HealthKitService.shared.getApprovedPermissionList()
            guard !approvedPermissions.isEmpty else { return }

            // Build payload using extracted notification data (safe across actor boundaries)
            let sentAt = DateTimeTools.getCurrentDatetimeIsoString()
            let timestamp = notification.entryTimestamp

            // Include the granted HealthKit permissions in the `data` payload so that the
            // backend can store which permissions were active when the entry was logged.
            let dataDict: [String: AnyCodable] = [
                "permissions": AnyCodable(approvedPermissions)
            ]

            // Fire-and-forget network call using notification's extracted data
            _ = try await apiRepository.logHealthIntegration(
                type: .healthKit,
                sentAt: sentAt,
                timestamp: timestamp,
                weight: notification.weight,
                bodyFat: notification.bodyFat,
                muscleMass: notification.muscleMass,
                water: notification.water,
                bmi: notification.bmi,
                data: dataDict
            )
            logger.log(level: .info, tag: tag, message: "Logged HealthKit integration entry to server. timestamp=\(timestamp), permissionsCount=\(approvedPermissions.count)")
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to log HealthKit integration", data: error.localizedDescription)
        }
    }
}
