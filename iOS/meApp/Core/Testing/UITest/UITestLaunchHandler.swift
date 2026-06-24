#if DEBUG
import Foundation

@MainActor
enum UITestLaunchHandler {
    static var isUITesting: Bool {
        UITestLaunchOptions.isUITesting
    }

    static func handleIfNeeded() {
        guard isUITesting else { return }
        // Intentionally left for future pre-launch data seeding/reset if needed.
    }

    static func registerMockServicesIfNeeded() {
        guard isUITesting else { return }
        UITestDependencyContainer.applyOverrides(scenario: UITestLaunchOptions.scenario)
    }
}
#endif
