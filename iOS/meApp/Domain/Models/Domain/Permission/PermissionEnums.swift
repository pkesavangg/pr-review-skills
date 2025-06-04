/// Enum representing all possible permission types in the app.
enum PermissionType: String, Codable, CaseIterable {
    case bluetooth
    case location
    case notification
    case camera
    // Add more as needed
}