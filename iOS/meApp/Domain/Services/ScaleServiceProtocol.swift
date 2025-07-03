import Foundation
import Combine

/// Protocol for business logic and orchestration related to paired-scale management.
///
/// This protocol defines high-level operations for paired scales, including listing, creating,
/// editing, deleting, updating scale meta and preferences, connection management, and pairing logic.
protocol ScaleServiceProtocol: DeviceServiceProtocol {

    /// The scales managed by the service.
    ///
    /// This is a BehaviorSubject that emits the current list of scales.
    /// The service will automatically update this subject when scales are added,
    /// edited, or deleted.
    ///
    /// The subject is updated on the main thread.
    var scalesPublisher: AnyPublisher<[Device], Never> { get }

    /// Updates scale meta data.
    /// - Parameters:
    ///   - scaleId: The ID of the scale.
    ///   - metaData: The meta data to update.
    func updateScaleMeta(_ deviceId: String, metaData: DeviceMetaData) async throws

    /// Updates scale preference.
    /// - Parameter preference: The R4ScalePreference to update.
    func updateScalePreference(_ deviceId: String, _ preference: R4ScalePreference) async throws

    /// Updates the status of a scale.
    /// - Parameters:
    ///   - scales: The scales to update.
    func updateAllScalesStatus(_ scales: [Device]?) async throws
}
