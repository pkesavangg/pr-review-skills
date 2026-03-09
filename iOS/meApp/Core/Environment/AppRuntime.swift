import Foundation

enum AppRuntime {
    /// True when app code is running inside XCTest/Swift Testing process.
    static var isRunningTests: Bool {
        ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil
    }
}
