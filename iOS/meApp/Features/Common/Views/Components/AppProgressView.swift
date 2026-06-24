//
//  AppProgressView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 11/06/25.
//

import SwiftUI

// MARK: - AppProgressView
/// A view that displays a progress indicator with a custom theme.
/// This view uses the `ProgressView` to show the current progress value.
struct AppProgressView: View {
    @Environment(\.appTheme) private var theme
    
    let progressValue: Double
    var body: some View {
        VStack {
            ProgressView(value: progressValue)
                .tint(theme.actionPrimary)
                .animation(.easeInOut, value: progressValue)
        }
    }
}

// MARK: - Preview
#Preview {
    AppProgressView(progressValue: 0.2)
        .environmentObject(Theme.shared)
}
