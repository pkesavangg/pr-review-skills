//
//  SettingsScreen.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 18/06/25.
//

import SwiftUI

// MARK: - SettingsScreen
// Represents the settings screen of the application.
// This screen allows users to configure various application settings.
struct SettingsScreen: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject private var tabViewModel: BottomTabBarViewModel
    @StateObject var settingsStore = SettingsStore()
    @StateObject private var router = Router<SettingsRoute>()
    // Dialog state controls
    // App-wide appearance picker state now in store

    let settingsLang = SettingsStrings.self
    let commonLang = CommonStrings.self
    let labels = InputFieldLabels.self
    let appAssets = AppAssets.self

    private let appearanceDisplayValue: (AppearanceMode) -> String = { $0.rawValue }
    private let notificationDisplayValue: (NotificationPreference) -> String = { $0.title }
    private let activityDisplayValue: (ActivityLevel) -> String = { $0.rawValue.capitalized }
    private let graphPeriodDisplayValue: (TimePeriod) -> String = { $0.title }

    var body: some View {
        RoutingView(stack: $router.stack) {
            VStack(spacing: 0) {
                NavbarHeaderView<EmptyView, EmptyView>(title: settingsLang.title, canShowBorder: true)
                ZStack {
                    theme.backgroundSecondary
                        .ignoresSafeArea()
                    VStack(spacing: 0) {
                        List {
                            profileHeader()
                            accountSettingsSection()
                            appSettingsSection()
                            if settingsStore.shouldShowWeightScaleSection {
                                weightScaleSection()
                            }
                            supportSection()
                            accountActionSection()
                        }
                        .listStyle(.insetGrouped)
                        .scrollContentBackground(.hidden)
                    }
                }
                .inAppBrowser(
                    url: settingsStore.presentingBrowserURL,
                    isPresented: settingsStore.isBrowserPresented
                )
            }
            // MOB-190: Hide the system navigation bar on the tab root. The custom
            // NavbarHeaderView is the only header. Without this, the NavigationStack's
            // system bar can reserve its height on the root after popping back from the
            // scale/Wi-Fi flow, pushing the "Settings" header down (leaked top inset).
            // Matches the pattern used by the other Settings screens (Integrations,
            // EditProfile, …); SwipeBackEnabler in RoutingView restores the swipe-back gesture.
            .navigationBarHidden(true)
            .onAppear {
                tabViewModel.showTabBar = true
                if tabViewModel.selectedTab == .settings {
                    settingsStore.presentAddAccountModalIfNeeded(router: router, tabViewModel: tabViewModel)
                    handlePendingSettingsNavigation()
                }

                tabViewModel.registerReselectHandler(for: .settings) {
                    tabViewModel.clearSettingsNavigationSource()
                    router.navigateToRoot()
                }
            }
            .onChange(of: tabViewModel.selectedTab) { oldTab, newTab in
                if newTab == .settings && oldTab != .settings {
                    settingsStore.presentAddAccountModalIfNeeded(router: router, tabViewModel: tabViewModel)
                }
            }
            .onChange(of: router.stack) { _, newStack in
                if newStack.isEmpty && tabViewModel.selectedTab == .settings {
                    if tabViewModel.settingsNavigationSourceTab != nil {
                        tabViewModel.returnToSettingsSourceTab()
                    }
                }
            }
            .onChange(of: tabViewModel.pendingSettingsNavigation) { _, _ in
                if tabViewModel.selectedTab == .settings {
                    handlePendingSettingsNavigation()
                }
            }

            .onChange(of: settingsStore.activeAccount?.accountId) { oldAccountId, newAccountId in
                guard oldAccountId != nil, newAccountId != oldAccountId else { return }
                router.navigateToRoot()
            }
        }
        .environmentObject(router)
        .environmentObject(settingsStore)
        // Appearance picker fallback for non-iPad or iOS>=18
        .pickerSheet(
            isPresented: $settingsStore.showAppearancePicker,
            selectedValues: [Theme.shared.appearanceMode],
            options: [AppearanceMode.allCases],
            displayValue: appearanceDisplayValue,
            title: settingsLang.appearance
        ) { vals in
            if let mode = vals.first {
                Theme.shared.appearanceMode = mode
            }
        }
        // Notifications picker
        .pickerSheet(
            isPresented: $settingsStore.showNotificationPicker,
            selectedValues: [settingsStore.notificationPreference],
            options: [NotificationPreference.allCases],
            displayValue: notificationDisplayValue,
            title: settingsLang.notifications
        ) { vals in
            if let pref = vals.first {
                settingsStore.updateNotificationPreference(pref)
            }
        }

        // Activity level picker
        .pickerSheet(
            isPresented: $settingsStore.showActivityPicker,
            selectedValues: [settingsStore.activeAccount?.activityLevel ?? .normal],
            options: [[ActivityLevel.normal, ActivityLevel.athlete]],
            displayValue: activityDisplayValue,
            title: settingsLang.activityLevel
        ) { vals in
            if let level = vals.first {
                settingsStore.updateActivityLevel(level)
            }
        }
        .pickerSheet(
            isPresented: $settingsStore.showDefaultGraphPeriodPicker,
            selectedValues: [settingsStore.defaultGraphPeriod],
            options: [TimePeriod.allCases],
            displayValue: graphPeriodDisplayValue,
            title: settingsLang.defaultGraphView
        ) { vals in
            if let period = vals.first {
                settingsStore.updateDefaultGraphPeriod(period)
            }
        }
    }

    // MOB-190: The profile header used to be a *loose* row in the List while every other
    // item is a `Section`. A loose row before Sections in an `.insetGrouped` List has no
    // stable section identity, so during scroll cell-recycling SwiftUI recomputed its
    // height/insets inconsistently (default separator bled in, centered content shifted or
    // overlapped) — the "header layout breaks on scroll" report. Wrapping it in its own
    // Section with a cleared background, hidden separator, and explicit insets makes it
    // lay out consistently like the rest of the List.
    private func profileHeader() -> some View {
        Section {
            profileHeaderContent()
                .accessibilityElement(children: .combine)
                .appAccessibility(id: AccessibilityID.settingsProfileHeader)
                .frame(maxWidth: .infinity)
                .listRowBackground(Color.clear)
                .listRowInsets(top: .spacingSM, bottom: .spacingSM)
                .listRowSeparator(.hidden)
        }
    }

    private func profileHeaderContent() -> some View {
        VStack(spacing: .spacingXS) {
            InitialIconView(
                character: settingsStore.profileInitial,
                size: 36,
                style: .fill
            )
            // MOB-223: A bare `.onLongPressGesture` (maximumDistance 10, non-simultaneous)
            // is unreliable inside a List — the scroll recognizer steals it and small
            // finger drift cancels it. Use the shared `.longPressGesture` (simultaneous,
            // maximumDistance 50) so the press coexists with scrolling and tolerates drift.
            .longPressGesture(isEditMode: false) {
                router.navigate(to: .myAccounts)
            }
            .accessibilityAction(named: SettingsStrings.A11y.profileSwitchAccountsAction) {
                router.navigate(to: .myAccounts)
            }
            Text(settingsStore.profileName)
                .fontOpenSans(.heading3)
                .foregroundColor(theme.textHeading)
                .lineLimit(1)
                .truncationMode(.tail)
            Text(settingsStore.profileEmail)
                .fontOpenSans(.body2)
                .foregroundColor(theme.textBody)
        }
    }

    private func accountSettingsSection() -> some View {
        Section(header: sectionHeader(title: settingsLang.accountSettings)) {
            ActionListItemView(config: ActionListItemConfig(title: settingsLang.userProfile) {
                router.navigate(to: .editProfile)
            })
            .listRowInsets()
            .accessibilityIdentifier(AccessibilityID.settingsRowUserProfile)
            // MOB-1226: "My Kids" is always enabled for every user — there is no disabled
            // state. Users with no baby profile land on the "No babies added yet" empty state.
            ActionListItemView(config: ActionListItemConfig(title: settingsLang.myKids) {
                router.navigate(to: .myKids)
            })
            .listRowInsets()
            .appAccessibility(id: AccessibilityID.settingsRowMyKids)
            ActionListItemView(config: ActionListItemConfig(title: settingsLang.addEditScales) { router.navigate(to: .addEditScales) })
            .listRowInsets()
            .accessibilityIdentifier(AccessibilityID.accountSettingsAddScalesRow)
            if settingsStore.shouldShowIntegrations {
                ActionListItemView(config: ActionListItemConfig(title: settingsLang.integrations) {
                    router.navigate(to: .integrations)
                })
                .listRowInsets()
                .accessibilityIdentifier(AccessibilityID.accountSettingsIntegrationsRow)
            }
            ActionListItemView(config: ActionListItemConfig(title: settingsLang.changePassword) {
                router.navigate(to: .changePassword)
            })
            .listRowInsets()
            .accessibilityIdentifier(AccessibilityID.accountSettingsChangePasswordRow)
        }
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtilityPrimary)
    }

    private func appSettingsSection() -> some View {
        Section(header: sectionHeader(title: settingsLang.appSettings)) {
            if settingsStore.shouldShowUnitType {
                ActionListItemView(config: ActionListItemConfig(
                    title: settingsLang.unitType,
                    value: settingsStore.unitTypeText,
                    chevronType: .upDown) { settingsStore.presentUnitPicker() })
                .listRowInsets()
                .appAccessibility(id: AccessibilityID.settingsRowUnitType)
            }
            ActionListItemView(config: ActionListItemConfig(title: settingsLang.appPermissions) {
                router.navigate(to: .appPermissions)
            })
            .listRowInsets()
            .appAccessibility(id: AccessibilityID.settingsRowAppPermissions)
            // Notifications are available for any product (weight / BP / baby), gated on
            // `shouldShowNotifications` rather than the weight-only section (was MOB-417).
            if settingsStore.shouldShowNotifications {
                ActionListItemView(config: ActionListItemConfig(
                    title: settingsLang.notifications,
                    value: settingsStore.notificationsOnText,
                    chevronType: .upDown) { settingsStore.presentNotificationPicker() })
                .listRowInsets()
                .appAccessibility(id: AccessibilityID.settingsRowNotifications)
            }
            ActionListItemView(config: ActionListItemConfig(
                title: settingsStore.messagesTitleText,
                showDot: settingsStore.canShowFeedNotificationBadge
            ) {
                router.navigate(to: .messages)
            })
            .id(settingsStore.canShowFeedNotificationBadge)
            .listRowInsets()
            .appAccessibility(id: AccessibilityID.settingsRowMessages)
            ActionListItemView(config: ActionListItemConfig(
                title: settingsLang.appearance,
                value: settingsStore.appearanceModeText,
                chevronType: .upDown) { settingsStore.presentAppearancePicker() })
            .listRowInsets()
            .appAccessibility(id: AccessibilityID.settingsRowAppearance)
            ActionListItemView(config: ActionListItemConfig(
                title: settingsLang.defaultGraphView,
                value: settingsStore.defaultGraphPeriodText,
                chevronType: .upDown) { settingsStore.presentDefaultGraphPeriodPicker() })
            .listRowInsets()
            .appAccessibility(id: AccessibilityID.settingsRowDefaultGraphView)
        }
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtilityPrimary)
    }

    private func weightScaleSection() -> some View {
        Section(header: sectionHeader(title: settingsLang.myWeight)) {
            ActionListItemView(config: ActionListItemConfig(
                title: settingsLang.goalSetting) {
                    router.navigate(to: .goal)
                })
            .listRowInsets()
            .accessibilityIdentifier(AccessibilityID.settingsRowGoalSetting)
            ActionListItemView(config: ActionListItemConfig(
                title: settingsLang.activityLevel,
                value: settingsStore.activityLevelText,
                chevronType: .upDown) { settingsStore.presentActivityPicker() })
            .listRowInsets()
            .accessibilityIdentifier(AccessibilityID.settingsRowActivityLevel)
            ActionListItemView(config: ActionListItemConfig(
                title: settingsLang.weightless,
                value: settingsStore.weightlessText) {
                    router.navigate(to: .weightless)
                })
            .listRowInsets()
            .accessibilityIdentifier(AccessibilityID.settingsRowWeightless)
        }
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtilityPrimary)
    }

    private func supportSection() -> some View {
        Section(header:
                    SectionHeader(title: settingsLang.supportSettings)
        ) {
            ActionListItemView(config: ActionListItemConfig(
                title: settingsLang.helpAndCustomerService
            ) {
                    router.navigate(to: .help)
                })
            .listRowInsets()
            .appAccessibility(id: AccessibilityID.settingsRowHelp)

            ActionListItemView(config: ActionListItemConfig(
                title: settingsLang.privacyPolicy
            ) {
                    settingsStore.openPrivacy()
                })
            .listRowInsets()
            .appAccessibility(id: AccessibilityID.settingsRowPrivacyPolicy)

            ActionListItemView(config: ActionListItemConfig(
                title: settingsLang.termsOfService
            ) {
                    settingsStore.openTerms()
                })
            .listRowInsets()
            .appAccessibility(id: AccessibilityID.settingsRowTermsOfService)

            ActionListItemView(config: ActionListItemConfig(
                title: settingsLang.greaterGoodsWebsite
            ) {
                    settingsStore.openGreaterGoods()
                })
            .listRowInsets()
            .appAccessibility(id: AccessibilityID.settingsRowGreaterGoods)
        }
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtilityPrimary)
    }

    private func accountActionSection() -> some View {
        Section {
            ActionListItemView(config: ActionListItemConfig(
                title: settingsLang.switchAccounts
            ) {
                    router.navigate(to: .myAccounts)
                })
            .listRowInsets()
            .appAccessibility(id: AccessibilityID.settingsRowSwitchAccounts)

            ActionListItemView(config: ActionListItemConfig(
                title: settingsLang.logOut,
                chevronType: .none
            ) {
                    settingsStore.handleLogout()
                })
            .listRowInsets()
            .accessibilityIdentifier(AccessibilityID.settingsRowLogOut)

            if settingsStore.canShowLogOutAllItems {
                ActionListItemView(config: ActionListItemConfig(
                    title: settingsLang.logOutAllAccount,
                    chevronType: .none
                ) {
                        settingsStore.handleLogoutForAllAccounts()
                    })
                .listRowInsets()
                .appAccessibility(id: AccessibilityID.settingsRowLogoutAll)
            }

            ActionListItemView(config: ActionListItemConfig(
                title: settingsLang.deleteAccount,
                chevronType: .none,
                isDestructive: true
            ) {
                    settingsStore.handleDeleteAccount()
                })
            .listRowInsets()
            .appAccessibility(id: AccessibilityID.settingsRowDeleteAccount)
        }
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtilityPrimary)
    }

    private func sectionHeader(title: String) -> some View {
        Text(title)
            .fontOpenSans(.heading4)
            .foregroundColor(theme.textHeading)
            .textCase(.none)
            .padding(.bottom, .spacingXS)
            .padding(.leading, -16)
    }

    // MARK: - Private Helpers
    /// Handles pending settings navigation by clearing the stack and navigating to the route
    private func handlePendingSettingsNavigation() {
        if let route = tabViewModel.pendingSettingsNavigation {
            tabViewModel.pendingSettingsNavigation = nil
            // Clear any existing navigation stack when navigating from external source
            router.navigateToRoot()
            router.navigate(to: route)
        }
    }

}

#Preview {
    SettingsScreen()
}
