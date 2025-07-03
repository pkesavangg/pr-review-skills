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

    // Internet
    static let internet       = "Internet"

    // Row titles / descriptions
    static let bluetoothAccessAuthorized = "Bluetooth access is authorized"
    static let bluetoothTurnedOn         = "Bluetooth is turned on"
    static let locationAccessEnabled     = "Location access is enabled"
    static let locationAccessNotAuthorized = "Location access not authorized"
    static let cameraAccessAuthorized    = "Camera access is authorized"
    static let notificationsEnabled      = "Notifications are enabled"

    // Wi-Fi row prompt (Location section)
    static let wifiEnablePrompt          = "Enable Wi-Fi to continue."

    // Row titles / descriptions for Internet
    static let internetNetworkConnected    = "Internet Network Connected."
    static let internetNetworkDisconnected = "Internet Network Disconnected."

    // Page header (Scale setup flows)
    static let pageHeaderTitle = "Permission Settings"
    static let cameraPermissionDescription = "Weight Gurus needs camera permissions to scan your scale."
    static let bluetoothPermissionDescription = "Weight Gurus needs Bluetooth permissions to connect with your scale."
    static let locationPermissionDescription = "Weight Gurus requires location access to view your Wi-Fi network information and connect to your scale."
} 