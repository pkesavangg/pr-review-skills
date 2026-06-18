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

    /// Full spoken date for VoiceOver (e.g. "June 11, 2025").
    private var spokenDateLabel: String {
        Self.spokenFormatter.string(from: date)
    }

    private static let spokenFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateStyle = .long
        f.timeStyle = .none
        return f
    }()

    var body: some View {
        ChipView(
            text: formattedDateString,
            style: .bordered,
            isSelected: isSelected,
            onTap: onTap
        )
        .accessibilityLabel(spokenDateLabel)
    }
}

#Preview {
    DateLabelView(date: Date()) {}
}
