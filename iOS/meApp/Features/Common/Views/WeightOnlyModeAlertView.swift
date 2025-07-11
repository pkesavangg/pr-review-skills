//
//  WeightOnlyModeAlertView.swift
//  meApp
//
//  Created by AI Assistant on 26/06/25.
//

import SwiftUI

/// A view that displays an alert when weight-only mode is enabled by other users on connected scales
/// Based on the Angular weight-only mode alert functionality
struct WeightOnlyModeAlertView: View {
    @StateObject private var store = WeightOnlyModeAlertStore()
    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationView {
            VStack(spacing: .spacingLG) {
                // Header
                headerView

                // Content
                if store.isLoading {
                    loadingView
                } else if store.weightOnlyScales.isEmpty {
                    emptyStateView
                } else {
                    scalesListView
                }

                Spacer()

                // Actions
                actionButtonsView
            }
            .padding(.spacingLG)
            .navigationTitle(WeightOnlyModeStrings.alertTitle)
            .navigationBarTitleDisplayMode(.inline)
            .navigationBarBackButtonHidden(true)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(CommonStrings.done) {
                        dismiss()
                    }
                    .foregroundColor(theme.textHeading)
                }
            }
        }
        .onAppear {
            store.loadWeightOnlyScales()
        }
    }

    // MARK: - Views

    private var headerView: some View {
        VStack(spacing: .spacingMD) {
            Image(systemName: "scale.mass.fill")
                .font(.largeTitle)
                .foregroundColor(theme.textHeading)

            Text(WeightOnlyModeStrings.alertTitle)
                .font(.heading4)
                .foregroundColor(theme.textHeading)
                .multilineTextAlignment(.center)

            Text(WeightOnlyModeStrings.alertMessage)
                .font(.body2)
                .foregroundColor(theme.textSubheading)
                .multilineTextAlignment(.center)
        }
    }

    private var loadingView: some View {
        VStack(spacing: .spacingMD) {
            ProgressView()
                .tint(theme.actionPrimary)

            Text("Loading scales...")
                .font(.body2)
                .foregroundColor(theme.textSubheading)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var emptyStateView: some View {
        VStack(spacing: .spacingMD) {
            Image(systemName: "scale.mass")
                .font(.largeTitle)
                .foregroundColor(theme.textSubheading)

            Text(WeightOnlyModeStrings.noScalesFoundMessage)
                .font(.body2)
                .foregroundColor(theme.textSubheading)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var scalesListView: some View {
        VStack(spacing: .spacingMD) {
            // Information note
            NoteBox {
                VStack(alignment: .leading, spacing: .spacingXS) {
                    Text(WeightOnlyModeStrings.noteTitle)
                        .font(.body2)
                        .fontWeight(.semibold)
                        .foregroundColor(theme.textHeading)

                    Text(WeightOnlyModeStrings.temporaryEnableNote)
                        .font(.body3)
                        .foregroundColor(theme.textSubheading)
                }
            }

            // Scales list
            LazyVStack(spacing: .spacingMD) {
                ForEach(store.weightOnlyScales, id: \.id) { scale in
                    scaleItemView(scale: scale)
                }
            }
        }
    }

    private func scaleItemView(scale: Device) -> some View {
        VStack(alignment: .leading, spacing: .spacingXS) {
            HStack {
                VStack(alignment: .leading, spacing: .spacingXS) {
                    Text(scale.nickname ?? scale.deviceName ?? "Unknown Scale")
                        .font(.body2)
                        .fontWeight(.medium)
                        .foregroundColor(theme.textHeading)

                    Text(WeightOnlyModeStrings.enabledByOthers)
                        .font(.body3)
                        .foregroundColor(theme.textSubheading)
                }

                Spacer()

                Button(CommonStrings.enable) {
                    store.enableBodyMetricsForScale(scale)
                }
                .font(.body3)
                .fontWeight(.medium)
                .foregroundColor(theme.actionPrimary)
            }

            Divider()
                .background(theme.backgroundSecondary)
        }
        .padding(.horizontal, .spacingMD)
        .padding(.vertical, .spacingXS)
        .background(theme.backgroundPrimary)
        .cornerRadius(.radiusMD)
        .overlay(
            RoundedRectangle(cornerRadius: .radiusMD)
                .stroke(theme.backgroundSecondary, lineWidth: 1)
        )
    }

    private var actionButtonsView: some View {
        VStack(spacing: .spacingMD) {
            Button(WeightOnlyModeStrings.dismissAlert) {
                dismiss()
            }
            .font(.button1)
            .foregroundColor(theme.actionPrimary)
            .frame(maxWidth: .infinity)
            .frame(height: 44)
            .background(theme.backgroundSecondary)
            .cornerRadius(.radiusMD)
        }
    }
}

// MARK: - Preview

#Preview {
    WeightOnlyModeAlertView()
        .environmentObject(Theme.shared)
}
