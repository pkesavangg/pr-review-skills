//
//  HistoryListScreen.swift
//  meApp
//
//  Created by Barath Chittibabu on 17/06/25.
//

import SwiftUI

/// Top-level screen that shows the monthly history summaries.
/// Uses `HistoryStore` for state and `MonthSummaryItem` for each row.
struct HistoryListScreen: View {
    @Environment(\.appTheme) private var theme
    @StateObject private var store = HistoryStore()
    @State private var navPath = NavigationPath()

    var body: some View {
        VStack(spacing: 0) {
            NavbarHeaderView<EmptyView, EmptyView>(
                title: HistoryListStrings.title,
                canShowBorder: true
            )
            .background(theme.backgroundPrimary)

            content
                .background(theme.backgroundSecondary)
                .edgesIgnoringSafeArea(.bottom)
        }
        .background(theme.backgroundSecondary)
        .onAppear {
            store.loadMonths()
        }
        .environmentObject(Theme.shared)
    }

    @ViewBuilder
    private var content: some View {
        if store.isLoading {
            // TODO: Add a loading state
            ProgressView()
                .progressViewStyle(.circular)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let message = store.errorMessage {

            ErrorStateView(message: message) {
                store.loadMonths()
            }
            .padding()
        } else if store.isEmptyState {
            // TODO: Add an empty state
            EmptyStateView(text: HistoryListStrings.emptyState)
                .padding()
        } else {
            List {
                ForEach(store.months, id: \.id) { month in
                        ZStack {
                          MonthSummaryItem(month: month, weightUnit: "kg")
                        }
                        .onTapGesture {
                            store.selectMonth(month)
                        }
                        .listRowBackground(Color.clear)
                        .listRowInsets(EdgeInsets())
                        .background(theme.backgroundSecondary)
                }
            }
            .listStyle(PlainListStyle())
            .scrollContentBackground(.hidden)
            .background(theme.backgroundSecondary)
        }
    }
}

// MARK: - Helper Views --------------------------------------------------

private struct ErrorStateView: View {
    let message: String
    let retry: () -> Void
    var body: some View {
        VStack(spacing: .spacingSM) {
            Text(message)
                .multilineTextAlignment(.center)
            Button(HistoryListStrings.retry) {
                retry()
            }
            .buttonStyle(.borderedProminent)
        }
    }
}

private struct EmptyStateView: View {
    let text: String
    var body: some View {
        VStack(spacing: .spacingSM) {
            Image(systemName: "tray")
                .font(.system(size: 40))
                .foregroundColor(.gray)
            Text(text)
                .fontOpenSans(.body2)
                .foregroundColor(.gray)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - Preview -------------------------------------------------------

#if DEBUG
struct HistoryListScreen_Previews: PreviewProvider {
    static var previews: some View {
        HistoryListScreen()
            .themeable()
            .environmentObject(Theme.shared)
    }
}
#endif

