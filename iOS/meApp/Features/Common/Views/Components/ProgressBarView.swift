//
//  ProgressBarView.swift
//  meApp
//
//  Created by Lakshmi Priya on 11/06/25.
//

import SwiftUI

/// A customizable linear progress bar with left/right labels and optional color.
struct ProgressBarView: View {
    @Environment(\.appTheme) var theme
    /// Progress value between 0.0 and 1.0
    var progress: CGFloat
    /// Label displayed on the left below the bar
    var leftLabel: String
    /// Label displayed on the right below the bar
    var rightLabel: String
    /// Optional color for the progress bar (defaults to theme.actionPrimary)
    var progressBarColor: Color?
    
    var body: some View {
        VStack(spacing: 8) {
            ProgressView(value: Double(progress))
                .progressViewStyle(LinearProgressViewStyle(tint: progressBarColor ?? theme.actionPrimary))
                .scaleEffect(x: 1, y: 2, anchor: .center)
                .clipShape(RoundedRectangle(cornerRadius: .radiusPill))
                .animation(.easeInOut(duration: 0.3), value: progress)
            HStack {
                Text(leftLabel)
                    .fontOpenSans(.subHeading2)
                    .foregroundColor(theme.actionTertiaryDisabled)
                Spacer()
                Text(rightLabel)
                    .fontOpenSans(.subHeading2)
                    .foregroundColor(theme.actionTertiaryDisabled)
            }
            .padding(.horizontal)
        }
        .padding(.top)
        .padding(.horizontal)
    }
}

/// Preview for ProgressBarView with sample values.
struct CustomProgressBar_Previews: PreviewProvider {
    static var previews: some View {
        ProgressBarView(progress: 0.13, leftLabel: "000", rightLabel: "000")
        ProgressBarView(progress: 0.5, leftLabel: "025", rightLabel: "100", progressBarColor: .blue)
            .previewLayout(.sizeThatFits)
    }
}

/*
// Test view animating progress from 1 to 100
// Demonstrates ProgressBarView with animated progress and custom labels.
import SwiftUI

struct ProgressBarTestView: View {
    @State private var currentValue: Int = 50 // First progress bar value
    @State private var currentValue2: Int = 78 // Second progress bar value
    let minValue: Int = 0
    let maxValue: Int = 99
    
    // Helper to format with leading zeros
    private func padded(_ value: Int) -> String {
        String(format: "%02d", value)
    }
    
    var body: some View {
        VStack(spacing: 32) {
            // First progress bar
            ProgressBarView(
                progress: CGFloat(currentValue - minValue) / CGFloat(maxValue - minValue),
                leftLabel: padded(currentValue),
                rightLabel: padded(maxValue)
            )
            .padding()
            
            // Second progress bar with custom color
            ProgressBarView(
                progress: CGFloat(currentValue2 - minValue) / CGFloat(maxValue - minValue),
                leftLabel: padded(currentValue2),
                rightLabel: padded(maxValue),
                progressBarColor: .green100
            )
            .padding()
            
            // Button to start animation
            ButtonView(text: "Start", type: .primary, size: .regular, isDisabled: false, action: {
                startAnimation()
            })
            .padding()
        }
    }
    
    // Animate both progress bars from 0 to 99
    func startAnimation() {
        currentValue = minValue
        currentValue2 = minValue
        Timer.scheduledTimer(withTimeInterval: 0.03, repeats: true) { timer in
            if currentValue < maxValue {
                currentValue += 1
            }
            if currentValue2 < maxValue {
                currentValue2 += 1
            }
            if currentValue >= maxValue && currentValue2 >= maxValue {
                timer.invalidate()
            }
        }
    }
}

struct ProgressBarTestView_Previews: PreviewProvider {
    static var previews: some View {
        ProgressBarTestView()
    }
}
*/
