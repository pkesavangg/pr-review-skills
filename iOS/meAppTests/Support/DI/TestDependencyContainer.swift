import Foundation
@testable import meApp

@MainActor
enum TestDependencyContainer {
    static func reset() {
        DependencyContainer.shared.dependencies.removeAll()
    }

    static func registerBase(
        logger: LoggerServiceProtocol,
        keychain: KeychainServiceProtocol,
        bluetooth: BluetoothServiceProtocol
    ) {
        DependencyContainer.shared.register(logger as LoggerServiceProtocol)
        DependencyContainer.shared.register(keychain as KeychainServiceProtocol)
        DependencyContainer.shared.register(bluetooth as BluetoothServiceProtocol)
    }
}
