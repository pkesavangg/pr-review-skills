import Foundation
import GGBluetoothSwiftPackage
import ggWifiScalePackage
import Network

@MainActor
final class WifiScaleService: WifiPairedDeviceServiceProtocol {
    static let shared = WifiScaleService()

    private let apiRepo: WifiDeviceRepositoryAPIProtocol
    private let logger: LoggerServiceProtocol
    private let tag = "WifiScaleService"
    private let setupClient: WifiScaleSetupClientProtocol
    private let networkProvider: WifiNetworkStatusProviding
    private let wifiInfoProvider: WifiInfoProviding
    @Injector private var permissionsService: PermissionsServiceProtocol

    init(
        apiRepo: WifiDeviceRepositoryAPIProtocol? = nil,
        logger: LoggerServiceProtocol? = nil,
        setupClient: WifiScaleSetupClientProtocol? = nil,
        networkProvider: WifiNetworkStatusProviding? = nil,
        wifiInfoProvider: WifiInfoProviding? = nil,
        permissionsService: PermissionsServiceProtocol? = nil
    ) {
        self.apiRepo = apiRepo ?? WifiDeviceRepositoryAPI()
        self.logger = logger ?? LoggerService.shared
        self.setupClient = setupClient ?? BasicWifiScaleSetupClient()
        self.networkProvider = networkProvider ?? NetworkMonitorStatusProvider()
        self.wifiInfoProvider = wifiInfoProvider ?? SystemWifiInfoProvider()
        if let permissionsService {
            self.permissionsService = permissionsService
        }
    }
    
    /// Fetches the scale token for WiFi scale operations.
    /// - Parameter request: Optional parameter for the scale token request.
    /// - Returns: A WifiScaleTokenResponse containing the scale token.
    func getScaleToken(request: String?) async throws -> WifiScaleTokenResponse {
        logger.log(level: .info, tag: tag, message: "getScaleToken called with request: \(request ?? "nil")")
        
        do {
            let result = try await apiRepo.getScaleToken(request: request)
            logger.log(level: .info, tag: tag, message: "Successfully fetched scale token")
            return result
        } catch {
            logger.log(level: .error, tag: tag, message: "WiFi scale token request failed: \(error.localizedDescription)")
            throw error
        }
    }

    // MARK: Connected Wi-Fi Info
    /// Returns information about the currently connected Wi-Fi network, closely matching
    /// the `getConnectedWifiInfo()` implementation
    /// - Returns: `WifiStatus` describing connection + permission state.
    func getConnectedWifiInfo() async -> WifiStatus {
        var ssid = ""
        var bssid = ""
        var status: WifiConnectionStatus = .unknown
        var locationStatus: GGPermissionState = .NOT_REQUESTED

        // Capture current location permission switch value (falls back to NOT_DETERMINED)
        locationStatus = permissionsService.getPermissionState(.LOCATION_SWITCH) ?? .NOT_REQUESTED

        // Determine if Wi-Fi is enabled by inspecting the current interface type.
        let isWifiEnabled = networkProvider.isConnected && networkProvider.connectionType == .wifi

        if isWifiEnabled {
            status = .enabled

            // Only attempt to read SSID/BSSID when both Location permissions are granted.
            let locationGranted = (permissionsService.getPermissionState(.LOCATION_SWITCH) == .ENABLED &&
                                   permissionsService.getPermissionState(.LOCATION) == .ENABLED)
            if locationGranted {
                if let currentSsid = await wifiInfoProvider.currentSSID(), currentSsid != "<unknown ssid>" {
                    ssid = currentSsid
                }
                if let currentBssid = await wifiInfoProvider.currentBSSID() {
                    bssid = currentBssid
                }
                if !ssid.isEmpty { status = .connected }
            }
        } else {
            status = .disabled
        }
        return WifiStatus(status: status, locationStatus: locationStatus, ssid: ssid, bssid: bssid)
    }

    // MARK: - Smart-Connect Placeholders
    /// Stops any ongoing Wi-Fi smart-connect session.
    func stop() async {
        logger.log(level: .info, tag: tag, message: "Cancelling any ongoing Wi-Fi scale operations")
        setupClient.cancel()
    }
 
    /// Performs a SmartConfig Wi-Fi setup sequence.
    /// - Parameters:
    ///   - info:      The parameters required for the operation (SSID, password, token, etc.).
    func smartConnect(_ info: WifiSetupInfo) async throws {
        
        let config = makeConfig(from: info)
        logger.log(level: .info, tag: tag, message: "SmartConfig WiFi setup started")
        do {
            try await setupClient.smartConnect(config: config)
            logger.log(level: .info, tag: tag, message: "SmartConfig WiFi setup succeeded")
        } catch {
            logger.log(level: .error, tag: tag, message: "SmartConfig WiFi setup failed: \(error.localizedDescription)")
            throw error
        }
    }

    /// Performs an ESP-Touch Wi-Fi setup sequence.
    /// - Parameters:
    ///   - info:      The parameters required for the operation (SSID, password, token, etc.).
    func espSmartConnect(_ info: WifiSetupInfo) async throws {
        let config = makeConfig(from: info)
        logger.log(level: .info, tag: tag, message: "ESPTouch WiFi setup started")
        do {
            try await setupClient.espSmartConnect(config: config)
            logger.log(level: .info, tag: tag, message: "ESPTouch WiFi setup succeeded")
        } catch {
            logger.log(level: .error, tag: tag, message: "ESPTouch WiFi setup failed: \(error.localizedDescription)")
            throw error
        }
    }

    /// Performs an AP-Mode Wi-Fi configuration sequence.
    /// - Parameters:
    ///   - info:       The setup parameters (SSID, password, token, etc.).
    func apMode(_ info: WifiSetupInfo) async throws {
        
        let config = makeConfig(from: info)
        logger.log(level: .info, tag: tag, message: "AP-mode WiFi setup started")
        do {
            try await setupClient.apMode(config: config)
            logger.log(level: .info, tag: tag, message: "AP-mode WiFi setup succeeded")
        } catch {
            logger.log(level: .error, tag: tag, message: "AP-mode WiFi setup failed: \(error.localizedDescription)")
            throw error
        }
    }

    // MARK: - Helpers
    /// Builds a `WifiScaleConfig` from the provided `WifiSetupInfo`, supplying sensible defaults
    /// for missing optional parameters (empty string / zero).
    private func makeConfig(from info: WifiSetupInfo) -> WifiScaleConfig {
        WifiScaleConfig(
            ssid: info.ssid ?? "",
            bssid: info.bssid ?? "", // Optional
            password: info.password ?? "", // Optional
            token: info.token ?? "", // Optional
            userNumber: info.userNumber ?? 0
        )
    }
}
