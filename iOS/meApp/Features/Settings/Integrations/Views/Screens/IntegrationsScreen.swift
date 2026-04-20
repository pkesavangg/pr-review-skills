//  IntegrationsScreen.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 26/06/25.
//
import SwiftUI

// MARK: - IntegrationsScreen
/// Displays a list of supported integrations (Apple Health, Fitbit, My Fitness Pal).
struct IntegrationsScreen: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject var router: Router<SettingsRoute>
    @StateObject private var store = IntegrationStore()
    @StateObject private var oauthSession = OAuthWebSession()

    var body: some View {
        VStack(spacing: 0) {
            // Header
            NavbarHeaderView(
                title: IntegrationsStrings.title,
                leadingContent: { AppIconView(icon: AppAssets.chevronLeft) },
                trailingContent: { EmptyView() },
                onLeadingTap: { router.navigateBack() },
                canShowBorder: true
            )

            List {
                Section {
                    // Dedicated Apple Health row
                    HealthKitIntegrationListItemView()
                        .listRowInsets()

                    // Remaining integration providers
                    ForEach(store.integrations.filter { $0.type != .appleHealth }, id: \.id) { item in
                        IntegrationListItemView(
                            item: item
                        ) { store.selectIntegration(item: item) }
                        .listRowInsets()
                    }
                }
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .background(theme.backgroundSecondary.ignoresSafeArea())
            .navigationBarHidden(true)
        }
        // OAuth flow — use ephemeral ASWebAuthenticationSession so each reconnect
        // starts with a clean cookie jar (fixes Fitbit/MFP auto-login on reconnect).
        .onChange(of: store.showBrowser) { _, isShowing in
            guard isShowing, let url = store.browserURL else { return }
            store.showBrowser = false
            oauthSession.start(url: url) {
                store.refreshAccounts()
            }
        }
    }
}

// MARK: - Preview
#Preview {
    IntegrationsScreen()
        .environmentObject(Theme.shared)
        .environmentObject(Router<SettingsRoute>())
} 
