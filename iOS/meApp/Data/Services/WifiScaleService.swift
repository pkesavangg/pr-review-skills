import Foundation
import Network
import GGBluetoothSwiftPackage

@MainActor
final class WifiScaleService: WifiScaleServiceProtocol {
    static let shared = WifiScaleService()
    
    private let apiRepo: WifiScaleRepositoryAPIProtocol = WifiScaleRepositoryAPI()
    private let logger = LoggerService.shared
    private let tag = "WifiScaleService"
    
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

        return WifiStatus(status: status, locationStatus: locationStatus, ssid: ssid, bssid: bssid)
    }

    // MARK: - Smart-Connect Placeholders
    /// Stops any ongoing Wi-Fi smart-connect session. Stub implementation – to be
    /// filled in once the underlying SDK integration is available.
    func stop() async {
        logger.log(level: .debug, tag: tag, message: "WifiScaleService.stop() called – stub implementation")
        // TODO: Implement actual teardown logic once available.
    }

    /// Performs a Wi-Fi smart-connect operation. This is a stub so that call-sites
    /// compile; real implementation will be added in a follow-up task.
    ///
    /// - Parameters:
    ///   - info:     Parameters required for the smart-connect sequence.
    ///   - setupType: High-level setup flow variant (first / join / change / espTouchWifi).
    func smartConnect(_ info: WifiSetupInfo, _ setupType: WifiSetupType) async throws {
        logger.log(level: .info, tag: tag, message: "smartConnect called with info: \(info), setupType: \(setupType)")
        // TODO: Integrate with WifiSmartConnect wrapper / SDK.
    }

    /// ESP-Touch dedicated smart-connect. Currently **unused** as per interim
    /// requirements – left here for future work.
    func espSmartConnect(_ info: WifiSetupInfo, _ setupType: WifiSetupType) async throws {
        logger.log(level: .info, tag: tag, message: "espSmartConnect called with info: \(info), setupType: \(setupType)")
        // TODO: Integrate with WifiSmartConnect ESP-Touch once available.
    }

    /// Performs an AP-Mode Wi-Fi configuration sequence.
    /// Currently a stub awaiting SDK integration so that call-sites compile and
    /// the flow can be exercised end-to-end.
    ///
    /// - Parameters:
    ///   - info:       The setup parameters (SSID, password, token, etc.).
    ///   - setupType:  Which high-level setup flow is running (first / join / change / espTouchWifi).
    func apMode(_ info: WifiSetupInfo, _ setupType: WifiSetupType) async throws {
        logger.log(level: .info, tag: tag, message: "apMode called with info: \(info), setupType: \(setupType)")
        // TODO: Integrate with WifiSmartConnect AP-Mode once available.
    }
} 
