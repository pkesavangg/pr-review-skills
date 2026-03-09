import Foundation
@testable import meApp

@MainActor
final class MockAccountFlagRepositoryAPI: AccountFlagRepositoryAPIProtocol {
    var fetchAccountFlagsResult: Result<[AccountFlagDTO], Error> = .success([])
    var deleteAccountFlagResult: Result<Bool, Error> = .success(true)

    private(set) var fetchAccountFlagsCalls = 0
    private(set) var deleteAccountFlagCalls = 0
    private(set) var lastDeleteFlagId: String?

    func fetchAccountFlags() async throws -> [AccountFlagDTO] {
        fetchAccountFlagsCalls += 1
        return try fetchAccountFlagsResult.get()
    }

    func deleteAccountFlag(flagId: String) async throws -> Bool {
        deleteAccountFlagCalls += 1
        lastDeleteFlagId = flagId
        return try deleteAccountFlagResult.get()
    }
}
