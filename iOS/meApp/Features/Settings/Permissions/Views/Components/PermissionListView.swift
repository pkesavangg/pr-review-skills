import SwiftUI

// MARK: - PermissionRow

private struct PermissionRow {
    let title: String
    let isEnabled: Bool
    let permissionType: PermissionType
}

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
        case all, appSync, btWifi, bluetooth, wifi, bpm
    }

    // MARK: Dependencies
    @Environment(\.appTheme) private var theme
    @StateObject private var viewModel = PermissionsListViewModel()
    // MARK: Configuration
    private let categories: Set<PermissionCategory>
    private let requiredCategories: Set<PermissionCategory>
    private let headerTitle: String?
    private let headerDescription: String?
    private var setupType: SetupType = .all

    /// Generic initialiser – retains the previous behaviour where caller explicitly specifies the permission groups.
    init(
        categories: Set<PermissionCategory> = Set(PermissionCategory.allCases.filter { $0 != .internet }),
        requiredCategories: Set<PermissionCategory> = [.bluetooth, .location]
    ) {
        self.categories = categories
        self.requiredCategories = requiredCategories
        self.headerTitle = nil
        self.headerDescription = nil
    }

    /// Convenience initialiser for scale setup flows – only `setupType` is required.
    /// Internally resolves which permission sections to show, which are required, and what
    /// explanatory text to render in the page header.
    ///
    /// - Parameter setupType: The high-level scale setup variant.
    init(setupType: SetupType) {
        // Determine configuration based on setup type.
        var resolvedCategories: Set<PermissionCategory>
        var description: String
        var title: String?
        self.setupType = setupType
        switch setupType {
        case .all:
            resolvedCategories = Set(PermissionCategory.allCases)
            description = "" // no header; will be ignored via nil below
            title = nil
        case .appSync:
            resolvedCategories = [.camera]
            description = PermissionsStrings.cameraPermissionDescription
            title = nil
        case .btWifi:
            resolvedCategories = [.bluetooth, .internet]
            description = PermissionsStrings.locationPermissionDescription
            title = nil
        case .bluetooth:
            resolvedCategories = [.bluetooth]
            description = PermissionsStrings.bluetoothPermissionDescription
            title = nil
        case .wifi:
            resolvedCategories = [.location]
            description = PermissionsStrings.locationPermissionDescription
            title = nil
        case .bpm:
            resolvedCategories = [.bluetooth]
            description = BpmSetupStrings.A3Permissions.description
            title = BpmSetupStrings.A3Permissions.title
        }

        self.categories = resolvedCategories
        self.requiredCategories = [] // Initialize with no required categories to avoid showing red indicators when permissions are disabled
        self.headerTitle = title
        self.headerDescription = description.isEmpty ? nil : description
    }

    // MARK: Body
    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading) {
                if let headerDescription = headerDescription {
                    pageHeader(
                        title: headerTitle ?? PermissionsStrings.pageHeaderTitle,
                        description: headerDescription
                    )
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
        let rows: [PermissionRow]
        if setupType == .bpm {
            rows = [
                PermissionRow(
                    title: viewModel.bluetoothAuthorized
                        ? PermissionsStrings.bluetoothAccessAuthorized
                        : BpmSetupStrings.A3Permissions.authorizeBluetoothAccess,
                    isEnabled: viewModel.bluetoothAuthorized,
                    permissionType: .bluetooth
                ),
                PermissionRow(
                    title: viewModel.bluetoothPoweredOn
                        ? PermissionsStrings.bluetoothTurnedOn
                        : BpmSetupStrings.A3Permissions.bluetoothTurnedOff,
                    isEnabled: viewModel.bluetoothPoweredOn,
                    permissionType: .bluetoothSwitch
                )
            ]
        } else {
            rows = [
                PermissionRow(
                    title: viewModel.bluetoothPoweredOn
                        ? PermissionsStrings.bluetoothTurnedOn
                        : PermissionsStrings.turnOnBluetooth,
                    isEnabled: viewModel.bluetoothPoweredOn,
                    permissionType: .bluetoothSwitch
                ),
                PermissionRow(
                    title: viewModel.bluetoothAuthorized
                        ? PermissionsStrings.bluetoothAccessAuthorized
                        : PermissionsStrings.authorizeBluetoothAccess,
                    isEnabled: viewModel.bluetoothAuthorized,
                    permissionType: .bluetooth
                )
            ]
        }

        return sectionView(
            title: PermissionsStrings.bluetooth,
            rows: rows,
            category: .bluetooth
        )
    }

    private var locationSection: some View {
        // Base rows for location services
        var rows: [PermissionRow] =
            setupType == .bpm
            ? [
                PermissionRow(
                    title: viewModel.locationAuthorized
                        ? PermissionsStrings.locationAccessAuthorized
                        : BpmSetupStrings.A3Permissions.authorizeLocationAccess,
                    isEnabled: viewModel.locationAuthorized,
                    permissionType: .location
                ),
                PermissionRow(
                    title: viewModel.locationServicesEnabled
                        ? PermissionsStrings.locationAccessEnabled
                        : BpmSetupStrings.A3Permissions.locationTurnedOff,
                    isEnabled: viewModel.locationServicesEnabled,
                    permissionType: .locationSwitch
                )
            ]
            : [
                PermissionRow(
                    title: viewModel.locationServicesEnabled
                        ? PermissionsStrings.locationAccessEnabled
                        : PermissionsStrings.enableLocationServices,
                    isEnabled: viewModel.locationServicesEnabled,
                    permissionType: .locationSwitch
                ),
                PermissionRow(
                    title: viewModel.locationAuthorized
                        ? PermissionsStrings.locationAccessAuthorized
                        : PermissionsStrings.authorizeLocationAccess,
                    isEnabled: viewModel.locationAuthorized,
                    permissionType: .location
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
            rows.append(PermissionRow(title: wifiRowTitle, isEnabled: viewModel.wifiSwitchEnabled && !ssid.isEmpty, permissionType: .wifiSwitch))
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
                PermissionRow(
                    title: viewModel.cameraAuthorized
                        ? PermissionsStrings.cameraAccessAuthorized
                        : PermissionsStrings.authorizeCameraAccess,
                    isEnabled: viewModel.cameraAuthorized,
                    permissionType: .camera
                )
            ],
            category: .camera
        )
    }

    private var internetSection: some View {
        let rowTitle = viewModel.internetConnected ? PermissionsStrings.internetNetworkConnected : PermissionsStrings.internetNetworkDisconnected
        let sectionTitle = setupType == .btWifi ? "Network" : PermissionsStrings.internet
        return sectionView(
            title: sectionTitle,
            rows: [PermissionRow(title: rowTitle, isEnabled: viewModel.internetConnected, permissionType: .internet)],
            category: .internet
        )
    }

    private var notificationSection: some View {
        sectionView(
            title: PermissionsStrings.notification,
            rows: [
                PermissionRow(
                    title: viewModel.notificationsEnabled
                        ? PermissionsStrings.notificationsEnabled
                        : PermissionsStrings.enableNotifications,
                    isEnabled: viewModel.notificationsEnabled,
                    permissionType: .notification
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

    private func pageHeader(title: String, description: String) -> some View {
        VStack(alignment: .leading, spacing: .spacingXS) {
            Text(title)
                .fontOpenSans(.heading4)
                .foregroundColor(theme.textHeading)
            Text(description)
                .fontOpenSans(.body2)
                .foregroundColor(theme.textBody)
        }
    }

    private func statusIcon(for isEnabled: Bool, required: Bool, showsChevron: Bool = false, isRowDisabled: Bool = false) -> AnyView {
        // Choose icon: checkmark for enabled, permission disabled icon for disabled (only for permission screen).
        let icon = isEnabled ? AppAssets.checkMarkCircle : (setupType == .all ? AppAssets.permissionDisabled : AppAssets.minusCircleClear)

        // Determine colour:

        //    - Disabled text permission (isRowDisabled) ➜ statusIconSecondaryDisabled (#D0CCCA)
        //    - Disabled & not required ➜ statusIconSecondary (#7B726E)
        //    - Disabled & required ➜ statusError (red), except grey in Scale Setup
        let colour: Color = {
            if isEnabled { return theme.actionPrimary }
            if isRowDisabled { return theme.statusIconSecondaryDisabled }
			if icon == AppAssets.minusCircleClear { return theme.statusIconSecondary }
            // In Scale Setup (setupType != .all), always show grey for disabled permissions
            if setupType != .all { return theme.statusUtilityPrimary }
            if required { return theme.statusError }
            return theme.statusIconSecondary
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
                             rows: [PermissionRow],
                             category: PermissionCategory) -> some View {
        VStack(alignment: .leading) {
            sectionHeader(title)
            // Card container
            VStack(spacing: 0) {
                ForEach(Array(rows.enumerated()), id: \.0) { index, row in
                    // Disable the location access and Wi‑Fi rows when Location Services switch is OFF
                    let isRowDisabled = (
                        category == .location &&
                        !viewModel.locationServicesEnabled &&
                        (row.permissionType == .location || row.permissionType == .wifiSwitch)
                    )
                    let showsChevron = !row.isEnabled

                    ActionListItemView(config: ActionListItemConfig(
                        title: row.title,
                        chevronType: row.isEnabled ? .none : .right,
                        leadingIcon: statusIcon(
                            for: isRowDisabled ? false : row.isEnabled,
                            required: isRowDisabled ? false : isRequired(category),
                            showsChevron: showsChevron,
                            isRowDisabled: isRowDisabled
                        )
                    ) {
                            if !row.isEnabled && !isRowDisabled {
                                viewModel.handlePermission(row.permissionType)
                            }
                        })
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
