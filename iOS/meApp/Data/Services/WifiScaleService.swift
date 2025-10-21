import Foundation
import Network
import GGBluetoothSwiftPackage
import ggWifiScalePackage

@MainActor
final class WifiScaleService: WifiScaleServiceProtocol {
    static let shared = WifiScaleService()
    
    private let apiRepo: WifiScaleRepositoryAPIProtocol = WifiScaleRepositoryAPI()
    private let logger = LoggerService.shared
    private let tag = "WifiScaleService"
    private let wifiScale = BasicWifiScale()
    @Injector private var permissionsService: PermissionsService

    init() {}
    
    /// Fetches the scale token for WiFi scale operations.
    /// - Parameter r: Optional parameter for the scale token request.
    /// - Returns: A WifiScaleTokenResponse containing the scale token.
    func getScaleToken(r: String?) async throws -> WifiScaleTokenResponse {
        logger.log(level: .info, tag: tag, message: "getScaleToken called with r: \(r ?? "nil")")
        
        do {
            let result = try await apiRepo.getScaleToken(r: r)
            logger.log(level: .info, tag: tag, message: "Successfully fetched scale token: \(result.token)")
            return result
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to fetch scale token: \(error.localizedDescription)")
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
        let isWifiEnabled = NetworkMonitor.shared.isConnected && NetworkMonitor.shared.connectionType == .wifi

        if isWifiEnabled {
            status = .enabled

            // Only attempt to read SSID/BSSID when both Location permissions are granted.
            let locationGranted = (permissionsService.getPermissionState(.LOCATION_SWITCH) == .ENABLED &&
                                   permissionsService.getPermissionState(.LOCATION) == .ENABLED)
            if locationGranted {
                if let currentSsid = await WifiInfoService.currentSSID(), currentSsid != "<unknown ssid>" {
                    ssid = currentSsid
                }
                if let currentBssid = await WifiInfoService.currentBSSID() {
                    bssid = currentBssid
                }
                if !ssid.isEmpty { status = .connected }
            }
        } else {
            status = .disabled
        }

        print("Wi-Fi status getConnectedWifiInfo", WifiStatus(status: status, locationStatus: locationStatus, ssid: ssid, bssid: bssid))
        return WifiStatus(status: status, locationStatus: locationStatus, ssid: ssid, bssid: bssid)
    }

    // MARK: - Smart-Connect Placeholders
    /// Stops any ongoing Wi-Fi smart-connect session.
    func stop() async {
        logger.log(level: .info, tag: tag, message: "Cancelling any ongoing Wi-Fi scale operations")
        wifiScale.cancel()
    }
 
    /// Performs a SmartConfig Wi-Fi setup sequence.
    /// - Parameters:
    ///   - info:      The parameters required for the operation (SSID, password, token, etc.).
    func smartConnect(_ info: WifiSetupInfo) async throws {
        

        let config = makeConfig(from: info)
        logger.log(level: .info, tag: tag, message: "smartConnect called with info: \(info)", data: config)
        do {
            try await wifiScale.connect(config: config, mode: .smartConfig, timeout: 120)
            logger.log(level: .info, tag: tag, message: "Scale connected successfully via SmartConfig")
        } catch {
            logger.log(level: .error, tag: tag, message: "SmartConfig connection failed: \(error.localizedDescription)")
            throw error
        }
    }

    /// Performs an ESP-Touch Wi-Fi setup sequence.
    /// - Parameters:
    ///   - info:      The parameters required for the operation (SSID, password, token, etc.).
    func espSmartConnect(_ info: WifiSetupInfo) async throws {

        let config = makeConfig(from: info)
        logger.log(level: .info, tag: tag, message: "espSmartConnect called with info: \(info)", data: config)
        do {
            try await wifiScale.connect(config: config, mode: .esptouch, timeout: 60)
            logger.log(level: .info, tag: tag, message: "Scale connected successfully via ESPTouch")
        } catch {
            logger.log(level: .error, tag: tag, message: "ESPTouch connection failed: \(error.localizedDescription)")
            throw error
        }
    }

    /// Performs an AP-Mode Wi-Fi configuration sequence.
    /// - Parameters:
    ///   - info:       The setup parameters (SSID, password, token, etc.).
    func apMode(_ info: WifiSetupInfo) async throws {
        

        let config = makeConfig(from: info)
        logger.log(level: .info, tag: tag, message: "apMode called with info: \(info)", data: config)
        do {
            try await wifiScale.connect(config: config, mode: .apMode, timeout: 90)
            logger.log(level: .info, tag: tag, message: "Scale connected successfully via AP-Mode")
        } catch {
            logger.log(level: .error, tag: tag, message: "AP-Mode connection failed: \(error.localizedDescription)")
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
