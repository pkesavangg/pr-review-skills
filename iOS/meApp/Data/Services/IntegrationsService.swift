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
    
    // MARK: - Properties
    private let apiRepository: IntegrationRepositoryAPIProtocol
    private let localRepository: IntegrationRepositoryProtocol
    private let accountService: AccountServiceProtocol
    private let logger = LoggerService.shared
    
    /// Default initializer that creates its own dependencies.
    init() {
        self.apiRepository = IntegrationAPIRepository()
        self.localRepository = IntegrationRepository()
        self.accountService = AccountService.shared
    }
    
    /// Initializes the service with required dependencies.
    /// Use this initializer for testing or custom dependency injection.
    init(
        apiRepository: IntegrationRepositoryAPIProtocol,
        localRepository: IntegrationRepositoryProtocol,
        accountService: AccountServiceProtocol
    ) {
        self.apiRepository = apiRepository
        self.localRepository = localRepository
        self.accountService = accountService
    }
    
    // MARK: - Helper
    @Sendable
    private func getAccountId() async throws -> String {
        guard let account = try await accountService.getActiveAccount() else {
            throw NSError(domain: "IntegrationService", code: 1, userInfo: [NSLocalizedDescriptionKey: "No active account found"])
        }
        return String(describing: account.id)
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
        try await localRepository.setIntegrationData(accountId: accountId, info: nil)
        logger.log(level: .info, tag: "IntegrationService", message: "Successfully cleared local integration data")
    }
    
    func getStoredIntegrationData() async throws -> IntegrationInfo? {
        let accountId = try await getAccountId()
        return try await localRepository.getIntegrationData(accountId: accountId)
    }
    
    func setStoredIntegrationData(_ info: IntegrationInfo?) async throws {
        let accountId = try await getAccountId()
        try await localRepository.setIntegrationData(accountId: accountId, info: info)
        logger.log(level: .info, tag: "IntegrationService", message: "Successfully set integration data for provider \(info?.type.rawValue ?? "none")")
    }
    
    func checkIfIntegrationIsAlreadyUsed(type: IntegrationType) async throws -> Bool {
        let accountId = try await getAccountId()
        return try await localRepository.checkIfIntegrationIsAlreadyUsed(accountId: accountId, type: type)
    }
    
    func clearIntegrationStatus() async throws {
        let accountId = try await getAccountId()
        try await localRepository.clearIntegrationStatus(accountId: accountId)
        logger.log(level: .info, tag: "IntegrationService", message: "Successfully cleared integration status for account \(accountId)")
    }
}
