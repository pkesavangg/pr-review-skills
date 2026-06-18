//
//  MockBabyRepositoryAPI.swift
//  meAppTests
//
//  Test double for the remote Baby Profile CRUD (MOB-386).
//

import Foundation
@testable import meApp

@MainActor
final class MockBabyRepositoryAPI: BabyRepositoryAPIProtocol {
    var listResult: [BabyResponse] = []
    var listError: Error?
    var createResult: BabyResponse?
    var createError: Error?
    var updateResult: BabyResponse?
    var updateError: Error?
    var deleteError: Error?

    private(set) var listCalls = 0
    private(set) var createCalls = 0
    private(set) var updateCalls = 0
    private(set) var deleteCalls = 0
    private(set) var lastCreateRequest: BabyRequest?
    private(set) var lastUpdateId: String?
    private(set) var lastUpdateRequest: BabyRequest?
    private(set) var lastDeletedId: String?

    func listBabies() async throws -> [BabyResponse] {
        listCalls += 1
        if let listError { throw listError }
        return listResult
    }

    func createBaby(_ request: BabyRequest) async throws -> BabyResponse {
        createCalls += 1
        lastCreateRequest = request
        if let createError { throw createError }
        return createResult ?? BabyResponse(
            id: "server-baby-\(createCalls)",
            name: request.name,
            birthdate: request.birthdate,
            sex: request.sex,
            birthWeightDecigrams: request.birthWeightDecigrams,
            birthLengthMillimeters: request.birthLengthMillimeters
        )
    }

    func updateBaby(_ babyId: String, _ request: BabyRequest) async throws -> BabyResponse {
        updateCalls += 1
        lastUpdateId = babyId
        lastUpdateRequest = request
        if let updateError { throw updateError }
        return updateResult ?? BabyResponse(
            id: babyId,
            name: request.name,
            birthdate: request.birthdate,
            sex: request.sex,
            birthWeightDecigrams: request.birthWeightDecigrams,
            birthLengthMillimeters: request.birthLengthMillimeters
        )
    }

    func deleteBaby(_ babyId: String) async throws {
        deleteCalls += 1
        lastDeletedId = babyId
        if let deleteError { throw deleteError }
    }
}
