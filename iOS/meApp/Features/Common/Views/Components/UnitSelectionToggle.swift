//
//  UnitSelectionToggle.swift
//  meApp
//
//  Created by Kiruthikayini S on 02/06/26.
//

import SwiftUI

// MARK: - UnitSelectionToggle
/// A compact pill-style two-option unit selector used in the signup flow.
///
/// The leading option represents the **imperial** unit (e.g. `Ft / In`, `lbs`) and the
/// trailing option the **metric** unit (e.g. `CM`, `kg`). Both options are driven by a
/// single `isMetric` flag, so the Height and Goal screens — which bind the same
/// `useMetric` form control — stay in sync: picking a metric unit on one screen flips the
/// other to its metric counterpart automatically (Imperial ↔ Metric stay paired).
struct UnitSelectionToggle: View {
    @Environment(\.appTheme) private var theme
    let imperialTitle: String
    let metricTitle: String
    @Binding var isMetric: Bool
    var accessibilityIdentifier: String?

    var body: some View {
        HStack(spacing: 0) {
            segment(title: imperialTitle, isSelected: !isMetric) { isMetric = false }
            segment(title: metricTitle, isSelected: isMetric) { isMetric = true }
        }
        .background(theme.backgroundPrimary)
        .clipShape(Capsule())
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier(accessibilityIdentifier ?? "")
    }

    private func segment(
        title: String,
        isSelected: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: {
            withAnimation(.spring(response: 0.45, dampingFraction: 0.85, blendDuration: 0)) {
                action()
            }
        }, label: {
            Text(title)
                .fontOpenSans(.button2)
                .foregroundColor(isSelected ? theme.textInverse : theme.actionSecondary)
                .frame(minWidth: 75)
                .padding(.horizontal, .spacingSM)
                .padding(.vertical, .spacingXS)
                .background(
                    Capsule().fill(isSelected ? theme.actionSecondary : Color.clear)
                )
        })
        .buttonStyle(.plain)
        .accessibilityIdentifier(title)
        .accessibilityAddTraits(isSelected ? [.isSelected, .isButton] : .isButton)
    }
}

// MARK: - Preview
#Preview {
    struct PreviewWrapper: View {
        @State private var isMetric = false
        var body: some View {
            VStack(spacing: 24) {
                UnitSelectionToggle(imperialTitle: "Ft / In", metricTitle: "CM", isMetric: $isMetric)
                UnitSelectionToggle(imperialTitle: "lbs", metricTitle: "kg", isMetric: $isMetric)
            }
            .padding()
            .background(Color.gray.opacity(0.2))
        }
    }
    return PreviewWrapper()
}
