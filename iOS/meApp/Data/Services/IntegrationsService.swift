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

    
    // MARK: - Initializer -------------------------------------------------
    /// Subscribes to `EntryService.entrySaved` so that every newly-created entry
    /// is automatically forwarded to the HealthKit log endpoint (if the account
    /// is integrated) without `EntryService` needing to know about integrations.
    init() {
        // Listen to new entries and forward to HealthKit when required.
        entryService.entrySaved
            .sink { [weak self] entry in
                // Fire-and-forget so the publisher chain is never blocked.
                Task { await self?.logHealthEntry(entry: entry) }
            }
            .store(in: &cancellables)
    }
    
    // MARK: - Helper
    @Sendable
    private func getAccountId() async throws -> String {
        guard let account = try await accountService.getActiveAccount() else {
            return ""
        }
        return account.accountId
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
        do {
            try await apiRepository.removeIntegration(accountId: accountId, provider: provider)
            logger.log(level: .info, tag: "IntegrationService", message: "Successfully removed integration from API for provider \(provider.rawValue)")
        } catch {
            logger.log(level: .error, tag: "IntegrationService", message: "Failed to remove integration from API: \(error.localizedDescription)")
            throw error
        }
        try localRepository.setIntegrationData(accountId: accountId, info: nil)
        logger.log(level: .info, tag: "IntegrationService", message: "Successfully cleared local integration data")
    }
    
    func getStoredIntegrationData() async throws -> IntegrationInfo? {
        let accountId = try await getAccountId()
        return try localRepository.getIntegrationData(accountId: accountId)
    }
    
    func setStoredIntegrationData(_ info: IntegrationInfo?) async throws {
        let accountId = try await getAccountId()
        try localRepository.setIntegrationData(accountId: accountId, info: info)
        if let integrationType = info?.type {
            do {
                try await accountService.updateIntegrations(integrationType: integrationType)
            } catch {
                logger.log(level: .error, tag: "IntegrationService", message: "Failed to update account integrations: \(error.localizedDescription)")
            }
        }
        
        logger.log(level: .info, tag: "IntegrationService", message: "Successfully set integration data for provider \(info?.type.rawValue ?? "none")")
    }
    
    func isIntegrationAlreadyUsed(type: IntegrationType) async throws -> Bool {
        let accountId = try await getAccountId()
        return try localRepository.isIntegrationAlreadyUsed(accountId: accountId, type: type)
    }
    
    func clearIntegrationStatus(integrationType: IntegrationType) async throws {
        let integrationInfo = IntegrationInfo(
            type: integrationType,
            isIntegrated: false
        )
        do {
            try await self.setStoredIntegrationData(integrationInfo)
            try await accountService.deleteHealthIntegration(integrationType)
        } catch {
            logger.log(level: .error, tag: "IntegrationService", message: "Failed to update account integrations after clearing status: \(error.localizedDescription)")
        }
    }
    
    // MARK: - Health Integration Logging ------------------------------------------------
    /// Sends the newly-created `Entry` to the `/integrations/health/log` endpoint when the
    /// current account is integrated with Apple Health and at least one permission is granted.
    ///
    /// Errors from the network layer are swallowed – only a log entry is written so that
    /// the caller (e.g. `EntryService`) never fails because of this background task.
    /// - Parameter entry: The just-saved entry that should be forwarded to Apple Health log.
    func logHealthEntry(entry: Entry) async {
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

            // Build payload
            let sentAt = DateTimeTools.getCurrentDatetimeIsoString()
            let timestamp = entry.entryTimestamp

            // Include the granted HealthKit permissions in the `data` payload so that the
            // backend can store which permissions were active when the entry was logged.
            let dataDict: [String: AnyCodable] = [
                "permissions": AnyCodable(approvedPermissions)
            ]

            // Fire-and-forget network call
            _ = try await apiRepository.logHealthIntegration(
                type: .healthKit,
                sentAt: sentAt,
                timestamp: timestamp,
                weight: entry.scaleEntry?.weight,
                bodyFat: entry.scaleEntry?.bodyFat,
                muscleMass: entry.scaleEntry?.muscleMass,
                water: entry.scaleEntry?.water,
                bmi: entry.scaleEntry?.bmi,
                data: dataDict
            )
        } catch {
            logger.log(level: .error, tag: "IntegrationService", message: "Failed to log HealthKit integration", data: error.localizedDescription)
        }
    }
}
