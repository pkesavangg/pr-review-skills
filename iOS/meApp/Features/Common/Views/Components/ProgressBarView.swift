//
//  ProgressBarView.swift
//  meApp
//
//  Created by Lakshmi Priya on 11/06/25.
//

import SwiftUI

/// A customizable linear progress bar with optional left/right labels and optional color.
struct ProgressBarView: View {
    @Environment(\.appTheme) var theme
    /// Progress value between 0.0 and 1.0
    var progress: CGFloat
    /// Optional label displayed on the left below the bar
    var leftLabel: String?
    /// Optional label displayed on the right below the bar
    var rightLabel: String?
    /// Optional color for the progress bar (defaults to theme.actionPrimary)
    var progressBarColor: Color?
    
    var body: some View {
        VStack(spacing: 8) {
            ProgressView(value: Double(progress))
                .progressViewStyle(LinearProgressViewStyle(tint: progressBarColor ?? theme.actionPrimary))
                .scaleEffect(x: 1, y: 2, anchor: .center)
                .clipShape(RoundedRectangle(cornerRadius: .radiusPill))
                .animation(.easeInOut(duration: 0.3), value: progress)
                .accessibilityLabel("Progress")
                .accessibilityValue("\(Int(progress * 100))%")
            
            if leftLabel != nil || rightLabel != nil {
                HStack {
                    if let leftLabel = leftLabel {
                        Text(leftLabel)
                            .fontOpenSans(.subHeading2)
                            .foregroundColor(theme.actionTertiaryDisabled)
                    }
                    Spacer()
                    if let rightLabel = rightLabel {
                        Text(rightLabel)
                            .fontOpenSans(.subHeading2)
                            .foregroundColor(theme.actionTertiaryDisabled)
                    }
                }
            }
        }
    }
}

/// Preview for ProgressBarView with sample values.
struct CustomProgressBar_Previews: PreviewProvider {
    static var previews: some View {
        Group {
            ProgressBarView(progress: 0.13, leftLabel: "000", rightLabel: "000")
            ProgressBarView(progress: 0.5, leftLabel: "025", rightLabel: "100", progressBarColor: .blue)
                .previewLayout(.sizeThatFits)
            ProgressBarView(progress: 0.33, leftLabel: "Label Only Left", rightLabel: nil)
            ProgressBarView(progress: 0.8, leftLabel: nil, rightLabel: "Only Right")
            ProgressBarView(progress: 0.5)
        }
        .previewLayout(.sizeThatFits)
    }
}

/*
// Test view animating progress from 1 to 100
// Demonstrates ProgressBarView with animated progress and custom labels.

struct ProgressBarTestView: View {
    @State private var currentValue: Int = 50
    let minValue: Int = 0
    let maxValue: Int = 99
    
    private func padded(_ value: Int) -> String {
        String(format: "%02d", value)
    }
    
    var body: some View {
        VStack(spacing: 32) {
            ProgressBarView(
                progress: CGFloat(currentValue - minValue) / CGFloat(maxValue - minValue),
                leftLabel: padded(currentValue),
                rightLabel: padded(maxValue)
            )
            Button("Start") {
                startAnimation()
            }
            .buttonStyle(.borderedProminent)
        }
        .padding()
    }
    
    func startAnimation() {
        currentValue = minValue
        Timer.scheduledTimer(withTimeInterval: 0.03, repeats: true) { timer in
            currentValue += (currentValue < maxValue) ? 1 : 0
            if currentValue >= maxValue { timer.invalidate() }
        }
    }
}

struct ProgressBarTestView_Previews: PreviewProvider {
    static var previews: some View { ProgressBarTestView() }
}
*/
