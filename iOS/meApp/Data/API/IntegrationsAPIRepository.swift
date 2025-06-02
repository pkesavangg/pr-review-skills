//
//  IntegrationsAPIRepository.swift
//  meApp
//
//  Created by Lakshmi Priya on 02/06/25.
//

import Foundation

@MainActor
final class IntegrationAPIRepository: IntegrationRepositoryAPIProtocol {
    private let httpClient = HTTPClient.shared
    
    func removeIntegration(accountId: String, provider: IntegrationType) async throws {
        // DELETE /integrations/{provider}
        let endpoint = Endpoint.integrationProvider(provider.rawValue)
        _ = try await httpClient.send(
            endpoint,
            method: .delete,
            body: EmptyBody(),
            needsAuth: true
        ) as EmptyResponse
    }    
}

