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
    /// Whether the date picker is currently open/selected.
    var isSelected: Bool = false
    /// Action to perform when the label is tapped.
    let onTap: () -> Void
    @Environment(\.appTheme) var theme

    /// Returns the formatted date string in 'MMM dd, yyyy' format, uppercased.
    var formattedDateString: String {
        return Self.formatter.string(from: date).uppercased()
    }

    /// Static cached DateFormatter for 'MMM dd, yyyy' format
    private static let formatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM dd, yyyy"
        return formatter
    }()

    var body: some View {
        ChipView(
            text: formattedDateString,
            style: .bordered,
            isSelected: isSelected,
            onTap: onTap
        )
    }
}

#Preview {
    DateLabelView(date: Date(), onTap: {})
}
