//
//  BabyReadingArrivalCTAView.swift
//  meApp
//

import SwiftUI

/// Two-button row rendered inside the baby scale reading arrival toast.
/// Secondary CTA (DON'T ASSIGN) is on the left; primary CTA (ASSIGN) is on the right.
struct BabyReadingArrivalCTAView: View {
    let onAssign: () -> Void
    let onDiscard: () -> Void

    var body: some View {
        HStack(spacing: .spacingSM) {
            ButtonView(
                text: BabyReadingArrivalStrings.dontAssign,
                type: .textPrimary,
                size: .small,
                isDisabled: false,
                action: onDiscard
            )
            Spacer()
            ButtonView(
                text: BabyReadingArrivalStrings.assign,
                type: .filledPrimary,
                size: .small,
                isDisabled: false,
                action: onAssign
            )
        }
    }
}
