import SwiftUI

// MARK: - PermissionListView
/// A standalone view that renders the grouped list of application permission states.
/// It mirrors the layout previously embedded directly inside `AppPermissionsScreen`.
struct PermissionListView: View {
    // MARK: Enumerations
    /// Distinct permission groups that can be rendered by the list.
    enum Category: CaseIterable, Hashable {
        case bluetooth, location, camera, notifications
    }
    
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
    @StateObject private var viewModel: PermissionsViewModel = PermissionsViewModel()
    var isFromScaleSetup: Bool = false
    // MARK: Configuration
    private let categories: Set<Category>
    private let requiredCategories: Set<Category>
    private let headerDescription: String?
    private var setupType: SetupType = .all
    
    /// Generic initialiser – retains the previous behaviour where caller explicitly specifies the permission groups.
    init(
        categories: Set<Category> = Set(Category.allCases),
        requiredCategories: Set<Category> = [.bluetooth, .location]
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
        let config: (Set<Category>, Set<Category>, String)
        self.setupType = setupType
        switch setupType {
        case .all:
            config = (Set(Category.allCases), [.bluetooth, .location], "") // no header; will be ignored via nil below
        case .appSync:
            config = ([.camera], [.camera], PermissionsStrings.cameraPermissionDescription)
        case .btWifi, .bluetooth:
            config = ([.bluetooth], [.bluetooth], PermissionsStrings.bluetoothPermissionDescription)
        case .wifi:
            config = ([.location], [.location], PermissionsStrings.locationPermissionDescription)
        }

        self.categories = config.0
        self.requiredCategories = []
        self.headerDescription = config.2.isEmpty ? nil : config.2
        self.isFromScaleSetup = true
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
                if categories.contains(.notifications) { notificationSection }
            }
        }
    }
    
    // MARK: Sections
    private var bluetoothSection: some View {
        sectionView(
            title: PermissionsStrings.bluetooth,
            rows: [
                (PermissionsStrings.bluetoothAccessAuthorized, viewModel.bluetoothAuthorized),
                (PermissionsStrings.bluetoothTurnedOn, viewModel.bluetoothPoweredOn)
            ],
            category: .bluetooth
        )
    }
    
    private var locationSection: some View {
        sectionView(
            title: PermissionsStrings.location,
            rows: [
                (PermissionsStrings.locationAccessEnabled, viewModel.locationServicesEnabled),
                (PermissionsStrings.locationAccessNotAuthorized, viewModel.locationAuthorized)
            ],
            category: .location
        )
    }
    
    private var cameraSection: some View {
        sectionView(
            title: PermissionsStrings.camera,
            rows: [
                (PermissionsStrings.cameraAccessAuthorized, viewModel.cameraAuthorized)
            ],
            category: .camera
        )
    }
    
    private var notificationSection: some View {
        sectionView(
            title: PermissionsStrings.notifications,
            rows: [
                (PermissionsStrings.notificationsEnabled, viewModel.notificationsEnabled)
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
            .padding(.top, .spacingMD)
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
    
    private func statusIcon(for isEnabled: Bool, required: Bool) -> AnyView {
        // Choose icon: checkmark for enabled, minus for disabled.
        let icon = isEnabled ? AppAssets.checkMarkCircle : AppAssets.minusCircleClear
        
        // Determine colour:
        //  - Enabled  ➜ primary action colour
        //  - Disabled & required  ➜ error (red)
        //  - Disabled & optional  ➜ utility / grey
        let colour: Color = {
            if isEnabled { return theme.actionPrimary }
            return required ? theme.statusError : theme.statusUtility
        }()
        
        return AnyView(
            AppIconView(icon: icon)
                .foregroundColor(colour)
        )
    }
    
    // MARK: Private helpers
    private func isRequired(_ category: Category) -> Bool {
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
                             rows: [(String, Bool)],
                             category: Category) -> some View {
        VStack(alignment: .leading) {
            sectionHeader(title)

            // Card container
            VStack(spacing: 0) {
                ForEach(Array(rows.enumerated()), id: \.0) { index, row in
                    ActionListItemView(config: ActionListItemConfig(
                        title: row.0,
                        chevronType: .none,
                        leadingIcon: statusIcon(for: row.1, required: isRequired(category))
                    ))
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
