// LoadingDotsView.swift
// meApp
// Created for animated three-dot loading effect

import SwiftUI

struct LoadingDotsView: View {
    let dotCount: Int = 3
    var jumpUpHeight: CGFloat = 6
    var jumpDownHeight: CGFloat = 2.5
    var dotSize: CGFloat = .spacingXS / 2
    var spacing: CGFloat = 4
    var animationDuration: Double = 1.5
    var color: Color = .primary

    var body: some View {
        TimelineView(.animation) { context in
            let time = context.date.timeIntervalSinceReferenceDate
            let progress = time.truncatingRemainder(dividingBy: animationDuration) / animationDuration

            HStack(spacing: spacing) {
                ForEach(0..<dotCount, id: \.self) { index in
                    let delay = delayFor(index: index)
                    let phase = (progress - delay + 1).truncatingRemainder(dividingBy: 1.0)

                    let (offsetY, scale) = interpolatedAnimationState(for: phase)

                    Circle()
                        .frame(width: dotSize, height: dotSize)
                        .foregroundColor(color)
                        .scaleEffect(scale)
                        .offset(y: offsetY)
                }
            }
        }
    }

    private func delayFor(index: Int) -> Double {
        switch index {
        case 0: return 0.2 / animationDuration
        case 1: return 0.4 / animationDuration
        case 2: return 0.55 / animationDuration
        default: return 0
        }
    }

    private func interpolatedAnimationState(for phase: Double) -> (CGFloat, CGFloat) {
        // Match keyframe intervals and interpolate between values
        switch phase {
        case 0..<0.4:
            return (0, 0.8)

        case 0.4..<0.58:
            // Interpolate from 0, 0.8 to -jumpUpHeight, 1.0
            let t = normalized(phase, in: 0.4...0.58)
            return (
                lerp(from: 0, to: -jumpUpHeight, t: t),
                lerp(from: 0.8, to: 1.0, t: t)
            )

        case 0.58..<0.62:
            // Interpolate to -jumpUpHeight * 0.95, 0.95
            let t = normalized(phase, in: 0.58...0.62)
            return (
                lerp(from: -jumpUpHeight, to: -jumpUpHeight * 0.95, t: t),
                lerp(from: 1.0, to: 0.95, t: t)
            )

        case 0.62..<0.75:
            // Interpolate to jumpDownHeight, 0.85
            let t = normalized(phase, in: 0.62...0.75)
            return (
                lerp(from: -jumpUpHeight * 0.95, to: jumpDownHeight, t: t),
                lerp(from: 0.95, to: 0.85, t: t)
            )

        case 0.75..<0.9:
            // Interpolate to 0, 0.8
            let t = normalized(phase, in: 0.75...0.9)
            return (
                lerp(from: jumpDownHeight, to: 0, t: t),
                lerp(from: 0.85, to: 0.8, t: t)
            )

        default:
            return (0, 0.8)
        }
    }

    private func lerp(from: CGFloat, to: CGFloat, t: CGFloat) -> CGFloat {
        from + (to - from) * t
    }

    private func normalized(_ value: Double, in range: ClosedRange<Double>) -> CGFloat {
        guard range.upperBound != range.lowerBound else { return 0 }
        return CGFloat((value - range.lowerBound) / (range.upperBound - range.lowerBound))
    }
}



#Preview {
    ZStack {
        Color.black.ignoresSafeArea()
        LoadingDotsView()
    }
}
