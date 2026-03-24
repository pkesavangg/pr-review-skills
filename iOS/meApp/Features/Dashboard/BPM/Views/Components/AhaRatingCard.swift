//
//  AhaRatingCard.swift
//  meApp
//
//  Compact AHA rating card. Tappable to open AhaRatingSheet.
//

import SwiftUI

struct AhaRatingCard: View {
    let classification: AhaPressureClass
    @Environment(\.appTheme) private var theme
    @State private var showSheet = false

    var body: some View {
        Button {
            showSheet = true
        } label: {
            HStack(spacing: .spacingSM) {
                RoundedRectangle(cornerRadius: 4)
                    .fill(classification.color(theme: theme))
                    .frame(width: 8, height: 32)

                VStack(alignment: .leading, spacing: 2) {
                    Text(BpmDashboardStrings.ahaRating)
                        .fontOpenSans(.body3)
                        .foregroundColor(theme.textSubheading)
                        .textCase(.uppercase)
                    Text(classification.label)
                        .fontOpenSans(.heading5)
                        .fontWeight(.bold)
                        .foregroundColor(classification.color(theme: theme))
                }

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.caption)
                    .foregroundColor(theme.textSubheading)
            }
            .padding(.spacingSM)
            .background(theme.backgroundPrimaryDisabled)
            .cornerRadius(12)
        }
        .buttonStyle(.plain)
        .sheet(isPresented: $showSheet) {
            AhaRatingSheet()
        }
        .accessibilityLabel("AHA rating: \(classification.label). Tap for details.")
    }
}
