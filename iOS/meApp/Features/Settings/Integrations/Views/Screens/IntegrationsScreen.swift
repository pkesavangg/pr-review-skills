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

                // Request new integration — sits just below the list section
                Section {
                    Button(action: { store.showRequestIntegrationModal() }) {
                        Text(IntegrationsStrings.requestNewIntegration)
                            .fontOpenSans(.button1)
                            .foregroundColor(theme.textHeading)
                            .frame(maxWidth: .infinity, alignment: .center)
                    }
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
        // In-app browser for OAuth flows
        .inAppBrowser(
            url: store.presentingBrowserURL,
            isPresented: Binding(
                get: { store.showBrowser },
                set: { store.showBrowser = $0 }
            )
        ) {
            store.refreshAccounts()
        }
    }
}

// MARK: - Preview
#Preview {
    IntegrationsScreen()
        .environmentObject(Theme.shared)
        .environmentObject(Router<SettingsRoute>())
} 
