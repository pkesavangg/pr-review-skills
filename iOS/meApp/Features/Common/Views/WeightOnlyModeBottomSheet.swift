//
//  WeightOnlyModeAlertView.swift
//  meApp
//
//  Created by AI Assistant on 14/08/25.
//

import SwiftUI

/// A view that displays an alert when weight-only mode is enabled by other users on connected scales
/// Based on the Angular weight-only mode alert functionality
struct WeightOnlyModeBottomSheet: View {
    @StateObject private var store = WeightOnlyModeAlertStore()
    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    let onDismiss: () -> Void
    let onEnableAllBodyMetrics: () -> Void

    private let commonLang = CommonStrings.self
    private let weightOnlyModeAlertLang = WeightOnlyModeAlertStrings.self

    init(onDismiss: @escaping () -> Void, onEnableAllBodyMetrics: @escaping () -> Void) {
        self.onDismiss = onDismiss
        self.onEnableAllBodyMetrics = onEnableAllBodyMetrics
    }

    var body: some View {
        VStack(spacing: .spacingXS) {
            // Close button – top-right aligned
            HStack {
                Spacer()
                Button {
                   onDismiss()
                   dismiss()
                } label: {
                    AppIconView(icon: AppAssets.close, size: IconSize(width: 16, height: 16))
                        .foregroundColor(theme.statusIconPrimary)
                }
                .appAccessibility(id: AccessibilityID.weightOnlyModeCloseButton)
            }

            VStack(spacing: .spacingMD) {
                // Header with icon and title
                headerView

                // Actions
                actionButtonsView
            }
        }
        .padding([.horizontal, .top], .spacingLG)
        .frame(maxWidth: .infinity)
        .background(theme.backgroundPrimary)
        .screenAccessibilityRoot(AccessibilityID.weightOnlyModeScreenRoot)
        .onAppear {
            store.loadWeightOnlyScales()
        }
    }

    // MARK: - Views

    private var headerView: some View {
        VStack(spacing: .spacingMD) {
           AppIconView(icon: AppAssets.weightOnlyModeAlertIconLarge, size: IconSize(width: 100, height: 100))
            .foregroundColor(theme.actionPrimary)

            VStack(spacing: .spacingXS) {
                Text(weightOnlyModeAlertLang.title)
                    .fontOpenSans(.heading4)
                    .foregroundColor(theme.textHeading)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .layoutPriority(1)

                Text(weightOnlyModeAlertLang.enableAllBodyMetrics)
                    .fontOpenSans(.body2)
                    .foregroundColor(theme.textSubheading)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .layoutPriority(1)
            }
        }
    }

    private var actionButtonsView: some View {
        VStack(spacing: .spacingXS) {
            // Primary button (as shown in mock)
            ButtonView(
                text: commonLang.enable,
                type: .filledPrimary,
                size: .large,
                isDisabled: false
            ) {
                    // Handle primary action
                  onEnableAllBodyMetrics()
                  store.handleEnableBodyMetrics()
                }
                .appAccessibility(id: AccessibilityID.weightOnlyModeEnableButton)

            ButtonView(
                text: commonLang.dismiss,
                type: .textPrimary,
                size: .large,
                isDisabled: false
            ) {
                  onDismiss()
                  store.dismissWeightOnlyModeAlert {
                    dismiss()
                  }
                }
                .appAccessibility(id: AccessibilityID.weightOnlyModeDismissButton)
        }
    }
}
