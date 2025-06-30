//
//  IntegrationsService.swift
//  meApp
//
//  Created by Lakshmi Priya on 03/06/25.
//

import Foundation

@MainActor
final class IntegrationsService: IntegrationServiceProtocol {
    static let shared = IntegrationsService()
    
    @Injector var accountService: AccountService
    @Injector var logger: LoggerService
    
    
    // MARK: - Properties
    private let apiRepository = IntegrationAPIRepository()
    private let localRepository = IntegrationRepository()

    
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
    
    func clearIntegrationStatus() async throws {
        let accountId = try await getAccountId()
        try localRepository.clearIntegrationStatus(accountId: accountId)
        do {
            try await accountService.deleteHealthIntegration(.healthKit)
        } catch {
            logger.log(level: .error, tag: "IntegrationService", message: "Failed to update account integrations after clearing status: \(error.localizedDescription)")
        }
        logger.log(level: .info, tag: "IntegrationService", message: "Successfully cleared integration status for account \(accountId)")
    }
}
