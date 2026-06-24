#if DEBUG
import Foundation

@MainActor
enum UITestDependencyContainer {
    static func applyOverrides(scenario: UITestLaunchOptions.Scenario) {
        let account = UITestAccountService(scenario: scenario)
        DependencyContainer.shared.register(account as AccountServiceProtocol)
    }
}
#endif
