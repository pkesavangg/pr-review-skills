//
//  BpmReadingArrivalCTAView.swift
//  meApp
//

import SwiftUI

/// BP reading values + two-button row rendered inside the BPM reading arrival toast.
/// Displays systolic/diastolic in success-green and pulse on the same row, with
/// DISCARD (text) and SAVE (filled primary) buttons below.
struct BpmReadingArrivalCTAView: View {
    @Environment(\.appTheme) private var theme

    let systolic: Int
    let diastolic: Int
    let pulse: Int
    let timestamp: String
    let onSave: () -> Void
    let onDiscard: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: .spacingSM) {
            HStack(alignment: .firstTextBaseline, spacing: .spacingMD) {
                bloodPressureText
                pulseText
            }
            .accessibilityElement(children: .combine)
            .accessibilityLabel(
                "\(systolic) over \(diastolic) \(DashboardStrings.bpmReadingArrivalMmhg), "
                    + "\(pulse) \(DashboardStrings.bpmReadingArrivalPulse), \(timestamp)"
            )

            HStack(spacing: .spacingSM) {
                ButtonView(
                    text: DashboardStrings.bpmReadingArrivalDiscard,
                    type: .textPrimary,
                    size: .small,
                    isDisabled: false,
                    action: onDiscard
                )
                Spacer()
                ButtonView(
                    text: DashboardStrings.bpmReadingArrivalSave,
                    type: .filledPrimary,
                    size: .small,
                    isDisabled: false,
                    action: onSave
                )
            }
        }
    }

    private var bloodPressureText: Text {
        Text("\(systolic)")
            .fontOpenSans(.heading4)
            .foregroundColor(theme.actionSuccess)
        + Text("/")
            .fontOpenSans(.heading4)
            .foregroundColor(theme.textHeading)
        + Text("\(diastolic)")
            .fontOpenSans(.heading4)
            .foregroundColor(theme.actionSuccess)
        + Text(" \(DashboardStrings.bpmReadingArrivalMmhg)")
            .fontOpenSans(.body2)
            .foregroundColor(theme.textHeading)
    }

    private var pulseText: Text {
        Text("\(pulse)")
            .fontOpenSans(.heading4)
            .foregroundColor(theme.textHeading)
        + Text(" \(DashboardStrings.bpmReadingArrivalPulse)")
            .fontOpenSans(.body2)
            .foregroundColor(theme.textHeading)
        + Text("  \(timestamp)")
            .fontOpenSans(.body4)
            .foregroundColor(theme.textSubheading)
    }
}
