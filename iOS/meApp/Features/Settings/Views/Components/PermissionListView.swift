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
    
    // MARK: Dependencies
    @Environment(\.appTheme) private var theme
    @StateObject private var viewModel: PermissionsViewModel = PermissionsViewModel()
    
    // MARK: Configuration
    private let categories: Set<Category>
    private let requiredCategories: Set<Category>
    
    /// Creates a configurable permission list.
    /// - Parameters:
    ///   - viewModel: State object holding the permission flags. Defaults to new instance.
    ///   - categories: Which permission groups to show. Defaults to **all**.
    ///   - requiredCategories: Which of the presented groups are mandatory for the current flow. Defaults to bluetooth & location.
    init(
        categories: Set<Category> = Set(Category.allCases),
        requiredCategories: Set<Category> = [.bluetooth, .location]
    ) {
        self.categories = categories
        self.requiredCategories = requiredCategories
    }
    
    // MARK: Body
    var body: some View {
        List {
            if categories.contains(.bluetooth) { bluetoothSection }
            if categories.contains(.location) { locationSection }
            if categories.contains(.camera) { cameraSection }
            if categories.contains(.notifications) { notificationSection }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
    }
    
    // MARK: Sections
    private var bluetoothSection: some View {
        Section(header: sectionHeader(PermissionsStrings.bluetooth)) {
            // Access authorised
            ActionListItemView(config: ActionListItemConfig(
                title: PermissionsStrings.bluetoothAccessAuthorized,
                chevronType: .none,
                leadingIcon: statusIcon(for: viewModel.bluetoothAuthorized, required: isRequired(.bluetooth))
            ))
            .listRowInsets()
            
            // BT powered on
            ActionListItemView(config: ActionListItemConfig(
                title: PermissionsStrings.bluetoothTurnedOn,
                chevronType: .none,
                leadingIcon: statusIcon(for: viewModel.bluetoothPoweredOn, required: isRequired(.bluetooth))
            ))
            .listRowInsets()
        }
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtility)
    }
    
    private var locationSection: some View {
        Section(header: sectionHeader(PermissionsStrings.location)) {
            ActionListItemView(config: ActionListItemConfig(
                title: PermissionsStrings.locationAccessEnabled,
                chevronType: .none,
                leadingIcon: statusIcon(for: viewModel.locationServicesEnabled, required: isRequired(.location))
            ))
            .listRowInsets()
            
            ActionListItemView(config: ActionListItemConfig(
                title: PermissionsStrings.locationAccessNotAuthorized,
                chevronType: .none,
                leadingIcon: statusIcon(for: viewModel.locationAuthorized, required: isRequired(.location))
            ))
            .listRowInsets()
        }
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtility)
    }
    
    private var cameraSection: some View {
        Section(header: sectionHeader(PermissionsStrings.camera)) {
            ActionListItemView(config: ActionListItemConfig(
                title: PermissionsStrings.cameraAccessAuthorized,
                chevronType: .none,
                leadingIcon: statusIcon(for: viewModel.cameraAuthorized, required: isRequired(.camera))
            ))
            .listRowInsets()
        }
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtility)
    }
    
    private var notificationSection: some View {
        Section(header: sectionHeader(PermissionsStrings.notifications)) {
            ActionListItemView(config: ActionListItemConfig(
                title: PermissionsStrings.notificationsEnabled,
                chevronType: .none,
                leadingIcon: statusIcon(for: viewModel.notificationsEnabled, required: isRequired(.notifications))
            ))
            .listRowInsets()
        }
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtility)
    }
    
    // MARK: Helpers
    private func sectionHeader(_ title: String) -> some View {
        Text(title)
            .fontOpenSans(.heading5)
            .foregroundColor(theme.textHeading)
            .textCase(.none)
            .padding(.bottom, .spacingXS)
            .padding(.leading, -16)
    }
    
    private func statusIcon(for isEnabled: Bool, required: Bool) -> AnyView {
        // Choose icon: checkmark for enabled, minus for disabled.
        let icon = isEnabled ? AppAssets.checkMarkCircle : AppAssets.xmark
        
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
}

// MARK: - Previews
struct PermissionsListView_Previews: PreviewProvider {
    static var previews: some View {
        Group {
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
