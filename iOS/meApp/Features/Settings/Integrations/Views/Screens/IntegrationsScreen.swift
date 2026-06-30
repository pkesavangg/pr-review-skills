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

    /// Integration providers other than Apple Health (Fitbit, My Fitness Pal).
    /// The store only populates these for weight-scale users.
    private var providerIntegrations: [IntegrationItem] {
        store.integrations.filter { $0.type != .appleHealth }
    }

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
                // Weight-scale-only providers (Fitbit, My Fitness Pal).
                // Hidden entirely when the user has no weight scale — the store
                // gates these items, so the section disappears when the list is empty.
                if !providerIntegrations.isEmpty {
                    Section(header: SectionHeader(title: IntegrationsStrings.weightScalesSectionTitle, fontStyle: .label1)) {
                        ForEach(providerIntegrations, id: \.id) { item in
                            IntegrationListItemView(
                                item: item
                            ) { store.selectIntegration(item: item) }
                            .listRowInsets()
                        }
                    }
                }

                // Apple Health — available to both weight-scale and BPM users.
                Section(header: SectionHeader(title: IntegrationsStrings.weightScalesAndBpmSectionTitle, fontStyle: .label1)) {
                    HealthKitIntegrationListItemView()
                        .listRowInsets()
                }

                // Request new integration — sits just below the list sections
                Section {
                    Button(action: { store.showRequestIntegrationModal() }, label: {
                        Text(IntegrationsStrings.requestNewIntegration)
                            .fontOpenSans(.label1)
                            .foregroundColor(theme.textHeading)
                            .frame(maxWidth: .infinity, alignment: .center)
                    })
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
                    .padding(.vertical, .spacingSM)
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
