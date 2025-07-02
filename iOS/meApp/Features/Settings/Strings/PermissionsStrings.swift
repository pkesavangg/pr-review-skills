/// Strings used in `AppPermissionsScreen`.
/// All static text displayed by the permissions settings feature lives here.
struct PermissionsStrings {
    // Screen title
    static let title = "Permissions"

    // Section titles
    static let bluetooth  = "Bluetooth"
    static let location   = "Location"
    static let camera     = "Camera for App Sync Scales"
    static let notifications = "Notifications"

    // Row titles / descriptions
    static let bluetoothAccessAuthorized = "Bluetooth access is authorized"
    static let bluetoothTurnedOn         = "Bluetooth is turned on"
    static let locationAccessEnabled     = "Location access is enabled"
    static let locationAccessNotAuthorized = "Location access not authorized"
    static let cameraAccessAuthorized    = "Camera access is authorized"
    static let notificationsEnabled      = "Notifications are enabled"

    // Page header (Scale setup flows)
    static let pageHeaderTitle = "Permission Settings"
    static let cameraPermissionDescription = "Weight Gurus needs camera permissions to scan your scale."
    static let bluetoothPermissionDescription = "Weight Gurus needs Bluetooth permissions to connect with your scale."
    static let locationPermissionDescription = "Weight Gurus requires location access to view your Wi-Fi network information and connect to your scale."
} 