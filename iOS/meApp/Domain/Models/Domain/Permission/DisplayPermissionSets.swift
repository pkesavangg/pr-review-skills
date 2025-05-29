import Foundation

struct DisplayPermissionSets: Codable, Equatable {
    var notifications: Bool
    var bluetooth: Bool
    var location: Bool
    var camera: Bool
}