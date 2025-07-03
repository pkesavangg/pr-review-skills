import SwiftUI

// MARK: - PermissionsViewModel
/// Holds the current on/off state for each app permission. For now the values are hard-coded; real permission checks will be wired in later.
@MainActor
class PermissionsViewModel: ObservableObject {
    // MARK: Published Permission Flags
    @Published var bluetoothAuthorized: Bool = true
    @Published var bluetoothPoweredOn: Bool = true
    @Published var locationServicesEnabled: Bool = true
    @Published var locationAuthorized: Bool = false
    @Published var cameraAuthorized: Bool = true
    @Published var notificationsEnabled: Bool = true
} 