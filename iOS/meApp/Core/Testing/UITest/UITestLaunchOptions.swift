#if DEBUG
import Foundation

enum UITestLaunchOptions {
    static let isUITestKey = "UITEST"
    static let scenarioKey = "UITEST_SCENARIO"

    enum Scenario: String {
        case loggedOut = "logged_out"
        case loginSuccess = "login_success"
        case loginUnauthorized = "login_unauthorized"
        case loginNetworkError = "login_network_error"
    }

    static var isUITesting: Bool {
        ProcessInfo.processInfo.environment[isUITestKey] == "1"
    }

    static var scenario: Scenario {
        let value = ProcessInfo.processInfo.environment[scenarioKey] ?? Scenario.loggedOut.rawValue
        return Scenario(rawValue: value) ?? .loggedOut
    }
}
#endif
