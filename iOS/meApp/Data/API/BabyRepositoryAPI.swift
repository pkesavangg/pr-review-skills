//
//  BabyRepositoryAPI.swift
//  meApp
//
//  Concrete remote Baby Profile CRUD adapter (MOB-386).
//

import Foundation

@MainActor
final class BabyRepositoryAPI: BabyRepositoryAPIProtocol {
    private let httpClient: HTTPClientProtocol

    init(httpClient: HTTPClientProtocol? = nil) {
        self.httpClient = httpClient ?? HTTPClient.shared
    }

    func listBabies() async throws -> [BabyResponse] {
        // GET /v3/baby/
        try await httpClient.get(.baby, needsAuth: true)
    }

    func createBaby(_ request: BabyRequest) async throws -> BabyResponse {
        // POST /v3/baby/
        try await httpClient.send(.baby, method: .post, body: request, needsAuth: true)
    }

    func updateBaby(_ babyId: String, _ request: BabyRequest) async throws -> BabyResponse {
        // PUT /v3/baby/:babyId
        try await httpClient.send(.babyId(babyId), method: .put, body: request, needsAuth: true)
    }

    func deleteBaby(_ babyId: String) async throws {
        // DELETE /v3/baby/:babyId (204 No Content)
        _ = try await httpClient.send(
            .babyId(babyId),
            method: .delete,
            body: EmptyBody(),
            needsAuth: true
        ) as EmptyResponse
    }
}
