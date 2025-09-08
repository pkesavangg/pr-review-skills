//
//  TimeLabelView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 17/06/25.
//

import SwiftUI

/// A button-styled label that displays a formatted time string and triggers an action when tapped.
struct TimeLabelView: View {
    /// Time to display (only hour & minute parts are considered).
    let time: Date
    /// Action to perform when tapped.
    var chipStyle: ChipStyle?
    let onTap: () -> Void
    @Environment(\.appTheme) private var theme

    /// Formatted time string (e.g., 9:41 AM).
    private var formattedTimeString: String {
        Self.formatter.string(from: time).uppercased()
    }

    /// Static cached DateFormatter for time.
    private static let formatter: DateFormatter = {
        let fmt = DateFormatter()
        fmt.dateFormat = "h:mm a"
        return fmt
    }()

    var body: some View {
        ChipView(text: formattedTimeString, style: chipStyle ?? .normal) {
            onTap()
        }
    }
}

#Preview {
    TimeLabelView(time: Date()) {}
        .environmentObject(Theme.shared)
} 
