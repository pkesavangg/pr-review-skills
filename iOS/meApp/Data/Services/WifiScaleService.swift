import Foundation

@MainActor
final class WifiScaleService: WifiScaleServiceProtocol {
    static let shared = WifiScaleService()
    
    private let apiRepo: WifiScaleRepositoryAPIProtocol = WifiScaleRepositoryAPI()
    private let logger = LoggerService.shared
    private let tag = "WifiScaleService"
    
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
} 