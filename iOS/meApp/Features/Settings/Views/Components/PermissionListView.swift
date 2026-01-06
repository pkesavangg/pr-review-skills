import SwiftUI

// MARK: - PermissionListView
/// A standalone view that renders the grouped list of application permission states.
/// It mirrors the layout previously embedded directly inside `AppPermissionsScreen`.
struct PermissionListView: View {
    /// High-level scale setup types that determine which permissions are required.
    /// – `appSync`  ➜ needs camera access only.
    /// – `btWifi`   ➜ needs Bluetooth access only (BT-WiFi scale).
    /// – `bluetooth`➜ needs Bluetooth access only (BT scale).
    /// – `wifi`     ➜ needs Location access only (Wi-Fi scale).
    enum SetupType {
        case all, appSync, btWifi, bluetooth, wifi
    }
    
    // MARK: Dependencies
    @Environment(\.appTheme) private var theme
    @StateObject private var viewModel: PermissionsListViewModel = PermissionsListViewModel()
    // MARK: Configuration
    private let categories: Set<PermissionCategory>
    private let requiredCategories: Set<PermissionCategory>
    private let headerDescription: String?
    private var setupType: SetupType = .all
    
    /// Generic initialiser – retains the previous behaviour where caller explicitly specifies the permission groups.
    init(
        categories: Set<PermissionCategory> = Set(PermissionCategory.allCases.filter { $0 != .internet }),
        requiredCategories: Set<PermissionCategory> = [.bluetooth, .location]
    ) {
        self.categories = categories
        self.requiredCategories = requiredCategories
        self.headerDescription = nil
    }
    
    /// Convenience initialiser for scale setup flows – only `setupType` is required.
    /// Internally resolves which permission sections to show, which are required, and what
    /// explanatory text to render in the page header.
    ///
    /// - Parameter setupType: The high-level scale setup variant.
    init(setupType: SetupType) {
        // Determine configuration based on setup type.
        let config: (Set<PermissionCategory>, String)
        self.setupType = setupType
        switch setupType {
        case .all:
            config = (Set(PermissionCategory.allCases), "") // no header; will be ignored via nil below
        case .appSync:
            config = ([.camera], PermissionsStrings.cameraPermissionDescription)
        case .btWifi:
            config = ([.bluetooth, .internet], PermissionsStrings.locationPermissionDescription)
        case .bluetooth:
            config = ([.bluetooth], PermissionsStrings.bluetoothPermissionDescription)
        case .wifi:
            config = ([.location], PermissionsStrings.locationPermissionDescription)
        }
        
        let (resolvedCategories, description) = config
        
        self.categories = resolvedCategories
        self.requiredCategories = [] // Initialize with no required categories to avoid showing red indicators when permissions are disabled
        self.headerDescription = description.isEmpty ? nil : description
    }
    
    // MARK: Body
    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading) {
                if let headerDescription = headerDescription {
                    pageHeader(description: headerDescription)
                        .padding([.top, .bottom], .spacingMD)
                }
                if categories.contains(.bluetooth) { bluetoothSection }
                if categories.contains(.location) { locationSection }
                if categories.contains(.camera) { cameraSection }
                if categories.contains(.internet) { internetSection }
                if categories.contains(.notifications) { notificationSection }
            }
        }
    }
    
    // MARK: Sections
    private var bluetoothSection: some View {
        sectionView(
            title: PermissionsStrings.bluetooth,
            rows: [
                (
                    viewModel.bluetoothPoweredOn
                        ? PermissionsStrings.bluetoothTurnedOn
                        : PermissionsStrings.turnOnBluetooth,
                    viewModel.bluetoothPoweredOn,
                    .bluetoothSwitch
                ),
                (
                    viewModel.bluetoothAuthorized
                        ? PermissionsStrings.bluetoothAccessAuthorized
                        : PermissionsStrings.authorizeBluetoothAccess,
                    viewModel.bluetoothAuthorized,
                    .bluetooth
                )                
            ],
            category: .bluetooth
        )
    }
    
    private var locationSection: some View {
        // Base rows for location services
        var rows: [(String, Bool, PermissionType)] = [
            (
                viewModel.locationServicesEnabled
                    ? PermissionsStrings.locationAccessEnabled
                    : PermissionsStrings.enableLocationServices,
                viewModel.locationServicesEnabled,
                .locationSwitch
            ),
            (
                viewModel.locationAuthorized
                    ? PermissionsStrings.locationAccessAuthorized
                    : PermissionsStrings.authorizeLocationAccess,
                viewModel.locationAuthorized,
                .location
            )
        ]
        
        // For Wi-Fi–only setup flows add an extra Wi-Fi status row
        if setupType == .wifi {
            let wifiRowTitle: String
            let ssid = viewModel.wifiNetworkName ?? ""
            if !ssid.isEmpty {
                wifiRowTitle = "Connected to \(ssid)"
            } else {
                wifiRowTitle = PermissionsStrings.wifiEnablePrompt
            }
            rows.append((wifiRowTitle, viewModel.wifiSwitchEnabled && !ssid.isEmpty, .wifiSwitch))
        }
        
        return sectionView(
            title: PermissionsStrings.location,
            rows: rows,
            category: .location
        )
    }
    
    private var cameraSection: some View {
        sectionView(
            title: PermissionsStrings.camera,
            rows: [
                (
                    viewModel.cameraAuthorized
                        ? PermissionsStrings.cameraAccessAuthorized
                        : PermissionsStrings.authorizeCameraAccess,
                    viewModel.cameraAuthorized,
                    .camera
                )
            ],
            category: .camera
        )
    }
    
    private var internetSection: some View {
        let rowTitle = viewModel.internetConnected ? PermissionsStrings.internetNetworkConnected : PermissionsStrings.internetNetworkDisconnected
        return sectionView(
            title: PermissionsStrings.internet,
            rows: [
                (
                    rowTitle,
                    viewModel.internetConnected,
                    .internet
                )
            ],
            category: .internet
        )
    }
    
    private var notificationSection: some View {
        sectionView(
            title: PermissionsStrings.notification,
            rows: [
                (
                    viewModel.notificationsEnabled
                        ? PermissionsStrings.notificationsEnabled
                        : PermissionsStrings.enableNotifications,
                    viewModel.notificationsEnabled,
                    .notification
                )
            ],
            category: .notifications
        )
    }
    
    // MARK: Helpers
    private func sectionHeader(_ title: String) -> some View {
        Text(title)
            .fontOpenSans(.heading5)
            .foregroundColor(theme.textHeading)
            .padding(.bottom, .spacingXS)
            .padding(.top, .spacingSM)
    }
    
    private func pageHeader(description: String) -> some View {
        VStack(alignment: .leading, spacing: .spacingXS) {
            Text(PermissionsStrings.pageHeaderTitle)
                .fontOpenSans(.heading4)
                .foregroundColor(theme.textHeading)
            Text(description)
                .fontOpenSans(.body2)
                .foregroundColor(theme.textBody)
        }
    }
    
    private func statusIcon(for isEnabled: Bool, required: Bool, showsChevron: Bool = false) -> AnyView {
        // Choose icon: checkmark for enabled, minus for disabled.
        let icon = isEnabled ? AppAssets.checkMarkCircle : AppAssets.minusCircleClear
        
        // Determine colour:
        //  - Enabled  ➜ primary action colour
        // Disabled but required → show error (red), except grey in Scale Setup
        //  - Disabled & optional  ➜ utility / grey
        let colour: Color = {
            if isEnabled { return theme.actionPrimary }
            // In Scale Setup (setupType != .all), always show grey for disabled permissions
            if setupType != .all { return theme.statusUtilityPrimary }
            if showsChevron || required { return theme.statusError }
            return theme.statusUtilityPrimary
        }()
        
        return AnyView(
            AppIconView(icon: icon)
                .foregroundColor(colour)
        )
    }
    
    // MARK: Private helpers
    private func isRequired(_ category: PermissionCategory) -> Bool {
        requiredCategories.contains(category)
    }
    
    /// Helper that generates a permission section with unified styling (mirrors the layout of the original `locationSection`).
    /// - Parameters:
    ///   - title: Localised title of the section.
    ///   - rows: Array of tuples `(title, enabled)` describing each row.
    ///   - category: The originating `Category`, used to evaluate `required` state for icons.
    /// - Returns: A view containing the header and a card-styled list of rows.
    @ViewBuilder
    private func sectionView(title: String,
                             rows: [(String, Bool, PermissionType)],
                             category: PermissionCategory) -> some View {
        VStack(alignment: .leading) {
            sectionHeader(title)
            // Card container
            VStack(spacing: 0) {
                ForEach(Array(rows.enumerated()), id: \.0) { index, row in
                    // Destructure the row tuple for better readability
                    let (label, isEnabled, permissionType) = row
                    // Disable the location access and Wi‑Fi rows when Location Services switch is OFF
                    let isRowDisabled = (
                        category == .location &&
                        !viewModel.locationServicesEnabled &&
                        (permissionType == .location || permissionType == .wifiSwitch)
                    )
                    let showsChevron = !isEnabled

                    ActionListItemView(config: ActionListItemConfig(
                        title: label,
                        chevronType: isEnabled ? .none : .right,
                        leadingIcon: statusIcon(
                            for: isRowDisabled ? false : isEnabled,
                            required: isRowDisabled ? false : isRequired(category),
                            showsChevron: showsChevron
                        ),
                        onTap: {
                            if !isEnabled && !isRowDisabled {
                                viewModel.handlePermission(permissionType)
                            }
                        }
                    ))
                    .allowsHitTesting(!isRowDisabled)
                    .opacity(isRowDisabled ? 0.5 : 1)
                    .padding(.horizontal)
                    
                    if index < rows.count - 1 {
                        Divider()
                            .frame(height: 1)
                            .padding(.leading, .spacingXL)
                    }
                }
            }
            .background(theme.backgroundPrimary)
            .clipShape(RoundedRectangle(cornerRadius: .radiusSM))
        }
    }
}

// MARK: - Previews
struct PermissionsListView_Previews: PreviewProvider {
    static var previews: some View {
        Group {
            PermissionListView()
                .environmentObject(Theme.shared)
                .padding(.horizontal)
                .background(.purple.opacity(0.3))
            
            PermissionListView(setupType: .wifi)
                .environmentObject(Theme.shared)
                .padding(.horizontal)
                .background(.purple.opacity(0.3))
            
            PermissionListView()
                .environmentObject(Theme.shared)
                .padding(.horizontal)
                .background(.purple.opacity(0.3))
            // AppSync flow – needs only camera
            PermissionListView(categories: [.camera])
                .background(Color.gray.opacity(0.31))
                .environmentObject(Theme.shared)
            
            // AppSync flow – needs only camera
            PermissionListView(categories: [.camera],
                               requiredCategories: [.camera])
            .background(Color.gray.opacity(0.31))
            .environmentObject(Theme.shared)
            // Scale-via-Bluetooth flow
            PermissionListView(categories: [.bluetooth],
                               requiredCategories: [.bluetooth])
            
            // Wi-Fi setup flow
            PermissionListView(categories: [.location],
                               requiredCategories: [.location])
            .background(Color.gray.opacity(0.31))
            .environmentObject(Theme.shared)
        }
    }
}
