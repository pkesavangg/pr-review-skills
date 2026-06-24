//
//  BabyRepositoryAPIProtocol.swift
//  meApp
//
//  Remote Baby Profile CRUD (MOB-386). Per the Baby App audit, only the four endpoints the
//  existing Baby app uses are wired: list, create, update, delete. The single-fetch and
//  shared-account/invitation endpoints are intentionally excluded.
//

import Foundation

protocol BabyRepositoryAPIProtocol {
    /// `GET /v3/baby/` — lists all baby profiles for the account.
    func listBabies() async throws -> [BabyResponse]

    /// `POST /v3/baby/` — creates a baby profile. The server auto-adds `"baby"` to the
    /// account's `productTypes`.
    func createBaby(_ request: BabyRequest) async throws -> BabyResponse

    /// `PUT /v3/baby/:babyId` — updates a baby profile (owner only).
    func updateBaby(_ babyId: String, _ request: BabyRequest) async throws -> BabyResponse

    /// `DELETE /v3/baby/:babyId` — deletes a baby profile. The server auto-removes `"baby"`
    /// from `productTypes` when the last baby is deleted. Responds 204 No Content.
    func deleteBaby(_ babyId: String) async throws
}
