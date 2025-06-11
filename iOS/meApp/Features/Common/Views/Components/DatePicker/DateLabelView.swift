//
//  DateLabelView.swift
//  meApp
//
//  Created by Lakshmi Priya on 11/06/25.
//

import SwiftUI

/// A button-styled label that displays a formatted date and triggers an action when tapped.
struct DateLabelView: View {
    /// The date to display.
    let date: Date
    /// Action to perform when the label is tapped.
    let onTap: () -> Void
    @Environment(\.appTheme) var theme

    /// Returns the formatted date string in 'MMM dd, yyyy' format, uppercased.
    var formattedDateString: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM dd, yyyy"
        return formatter.string(from: date).uppercased()
    }

    var body: some View {
        Button(action: onTap) {
            Text(formattedDateString)
                .fontWeight(.bold)
                .fontOpenSans(.link1)
                .foregroundColor(theme.actionPrimary)
                .padding(.horizontal, 11)
                .padding(.vertical, 6)
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .fill(theme.backgroundPrimary)
                        .cornerRadius(.radiusSM)
                )
        }
        .buttonStyle(PlainButtonStyle())
    }
}

#Preview {
    DateLabelView(date: Date(), onTap: {})
}
