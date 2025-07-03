import SwiftUI

// MARK: - PermissionsViewModel
/// Holds the current on/off state for each app permission. For now the values are hard-coded; real permission checks will be wired in later.
@MainActor
class PermissionsViewModel: ObservableObject {
    // MARK: Published Permission Flags
    @Published var bluetoothAuthorized: Bool = false
    @Published var bluetoothPoweredOn: Bool = false
    @Published var locationServicesEnabled: Bool = false
    @Published var locationAuthorized: Bool = false
    @Published var cameraAuthorized: Bool = false
    @Published var notificationsEnabled: Bool = false
}
