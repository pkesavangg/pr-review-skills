import XCTest

enum UITestScenario: String {
    case loggedOut = "logged_out"
    case loginSuccess = "login_success"
    case loginUnauthorized = "login_unauthorized"
    case loginNetworkError = "login_network_error"
}

enum UITestLaunchEnvironment {
    static let isUITestKey = "UITEST"
    static let scenarioKey = "UITEST_SCENARIO"
}

extension XCUIApplication {
    func launchForUITest(scenario: UITestScenario) {
        launchEnvironment[UITestLaunchEnvironment.isUITestKey] = "1"
        launchEnvironment[UITestLaunchEnvironment.scenarioKey] = scenario.rawValue
        launch()
    }
}
