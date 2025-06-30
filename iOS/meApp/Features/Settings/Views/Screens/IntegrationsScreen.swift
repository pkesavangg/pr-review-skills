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
            NavbarHeaderView<Image, EmptyView>(
                title: IntegrationsStrings.title,
                leadingContent: { Image(AppAssets.chevronLeft) },
                onLeadingTap: { router.navigateBack() },
                canShowBorder: true
            )

            List {
                Section {
                    ForEach(store.integrations, id: \.id) { item in
                        IntegrationListItemView(item: item) {
                            store.selectIntegration(item: item)
                        }
                        .listRowInsets()
                    }
                }
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .background(theme.backgroundSecondary.ignoresSafeArea())
            .navigationBarHidden(true)
        }
    }
}

// MARK: - Preview
#Preview {
    IntegrationsScreen()
        .environmentObject(Theme.shared)
        .environmentObject(Router<SettingsRoute>())
} 
