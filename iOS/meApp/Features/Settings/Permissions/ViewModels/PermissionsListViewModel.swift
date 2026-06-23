import Combine // Added for Combine
import GGBluetoothSwiftPackage
import SwiftUI
// MARK: - PermissionsListViewModel
/// Holds the current on/off state for each app permission. For now the values are hard-coded; real permission checks will be wired in later.
@MainActor
class PermissionsListViewModel: ObservableObject {
    @Injector var permissionsService: PermissionsServiceProtocol
    @Injector var loggerService: LoggerServiceProtocol
    @Injector var wifiDeviceService: WifiPairedDeviceServiceProtocol
    
    // MARK: Published Permission Flags
    @Published var bluetoothAuthorized: Bool = false
    @Published var bluetoothPoweredOn: Bool = false
    @Published var locationServicesEnabled: Bool = false
    @Published var locationAuthorized: Bool = false
    @Published var cameraAuthorized: Bool = true
    @Published var notificationsEnabled: Bool = true
    @Published var internetConnected: Bool = false
    @Published var wifiSwitchEnabled: Bool = false
    // Holds the currently connected Wi-Fi SSID (nil if not connected)
    @Published var wifiNetworkName: String?
    
    // Combine
    private var cancellables: Set<AnyCancellable> = []
    private let tag = "PermissionsViewModel"
    private let networkMonitor = NetworkMonitor.shared
    
    // MARK: - Initialiser
    init() {
        // Listen for permission updates from the central PermissionsService
        permissionsService.permissionsPublisher
        // Ensure we are on the main thread – PermissionsService is @MainActor but
        // we keep the explicit dispatch for safety when used from tests.
            .receive(on: DispatchQueue.main)
            .sink { [weak self] permissionDict in
                self?.apply(permissionDict)
                self?.refreshWifiNetworkName()
            }
            .store(in: &cancellables)
        networkMonitor.$isConnected
            .receive(on: DispatchQueue.main)
            .sink { [weak self] isConnected in
                self?.internetConnected = isConnected
                self?.refreshWifiNetworkName()
            }
            .store(in: &cancellables)
        networkMonitor.$connectionType
            .receive(on: DispatchQueue.main)
            .sink { [weak self] connectionType in
                guard let self else { return }
                if connectionType == .wifi {
                    self.refreshWifiNetworkName()
                } else {
                    self.wifiNetworkName = nil
                }
            }
            .store(in: &cancellables)
        refreshWifiNetworkName()
    }
    
    // MARK: - Mapping Helpers
    /// Applies the latest permission dictionary to the view-model's published flags.
    private func apply(_ permissions: [GGPermissionType: GGPermissionState]?) {
        guard let permissions = permissions else {
            // No data yet – reset all to false to avoid stale state
            bluetoothAuthorized = false
            bluetoothPoweredOn = false
            locationServicesEnabled = false
            locationAuthorized = false
            cameraAuthorized = false
            notificationsEnabled = false
            internetConnected = false
            wifiSwitchEnabled = false
            return
        }
        
        // Helper function to check if a permission state is granted
        func isGranted(_ state: GGPermissionState?) -> Bool {
            return state == .ENABLED
        }
        
        bluetoothAuthorized     = isGranted(permissions[.BLUETOOTH])
        bluetoothPoweredOn      = isGranted(permissions[.BLUETOOTH_SWITCH])
        locationServicesEnabled = isGranted(permissions[.LOCATION_SWITCH])
        locationAuthorized      = isGranted(permissions[.LOCATION])
        cameraAuthorized        = isGranted(permissions[.CAMERA])
        notificationsEnabled    = isGranted(permissions[.NOTIFICATION])
        wifiSwitchEnabled = isGranted(permissions[.WIFI_SWITCH])
    }
    
    func handlePermission(_ type: PermissionType) {
        Task {
           let result = await permissionsService.handlePermission(type)
           loggerService.log(level: .info, tag: tag, message: "Handled permission \(type): \(result)")
        }
    }

    private func refreshWifiNetworkName() {
        Task { [weak self] in
            self?.wifiNetworkName = await self?.wifiDeviceService.getConnectedWifiInfo().ssid
        }
    }
}
