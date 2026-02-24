//
//  AppSyncEntryCardView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 08/07/25.
//

import SwiftUI

// MARK: - AppSyncEntryCardView
/// Confirmation card shown after an AppSync scan finishes successfully.
/// Displays the scanned body composition metrics and provides **Save** and **Edit** actions.
///
/// All values are injected so that no hard-coded dummy data lives in the view.
/// Pass empty strings if you wish to show "--" or a placeholder upstream.
struct AppSyncEntryCardView: View {
    // Theme colours & spacing
    @Environment(\.appTheme) private var theme

    // MARK: – Dependencies
    let metrics: AppSyncEntryMetrics

    let onSave: () -> Void
    let onEdit: () -> Void

    // MARK: – Body
    var body: some View {
        VStack(spacing: .spacingSM) {
            // Title
            Text(AppSyncEntryCardStrings.title)
                .fontOpenSans(.heading4)
                .multilineTextAlignment(.center)
                .foregroundColor(theme.textHeading)
                .padding(.horizontal, .spacingXS)
            // Metrics list
            VStack(spacing: .spacingXS) {
                metricText(label: AppSyncEntryCardStrings.weight, value: metrics.weight)
                metricText(label: AppSyncEntryCardStrings.bodyFat, value: metrics.bodyFat)
                metricText(label: AppSyncEntryCardStrings.muscleMass, value: metrics.muscleMass)
                metricText(label: AppSyncEntryCardStrings.waterWeight, value: metrics.waterWeight)
                metricText(label: AppSyncEntryCardStrings.bmi, value: metrics.bmi)
            }

            // Primary save button
            ButtonView(
                text: CommonStrings.save,
                type: .filledPrimary,
                size: .large,
                isDisabled: false,
                action: onSave
            )
            // Secondary edit button
            ButtonView(
                text: CommonStrings.edit,
                type: .textPrimary,
                size: .large,
                isDisabled: false,
                action: onEdit
            )
        }
        .padding(.spacingMD)
        .padding(.vertical, .spacingSM)
        .background(theme.backgroundSecondary)
        .cornerRadius(.radiusXL)
    }

    // MARK: – Helpers
    private func metricText(label: String, value: String?) -> some View {
// swiftlint:disable:next force_unwrapping
        let displayValue = (value?.isEmpty == false) ? value! : "--"
        return Text("\(label): \(displayValue)")
            .fontOpenSans(.body2)
            .foregroundColor(theme.textBody)
    }
}

// MARK: – Preview
#if DEBUG
struct AppSyncEntryCardView_Previews: PreviewProvider {
    static var previews: some View {
        AppSyncEntryCardView(
            metrics: AppSyncEntryMetrics(
                storedWeight: 220,
                storedBMI: 4,
                storedBodyFat: 609,
                storedWaterWeight: 730,
                storedMuscleMass: 3,
                isMetric: true
            ),
            onSave: {},
            onEdit: {}
        )
        .environmentObject(Theme.shared)
        .padding()
        .background(Color.black.opacity(0.8))
    }
}
#endif 
