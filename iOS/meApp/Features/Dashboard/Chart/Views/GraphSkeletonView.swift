//
//  GraphSkeletonView.swift
//  meApp
//
//  Skeleton loader view displayed while graph is computing/settling
//

import SwiftUI

struct GraphSkeletonView: View {
    var height: CGFloat = 265

    @Environment(\.appTheme) private var theme
    @State private var isAnimating = false

    private let yAxisTickCount = 5
    private let xAxisTickCount = 8

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Weight label placeholder
            RoundedRectangle(cornerRadius: 4)
                .fill(skeletonColor)
                .frame(width: 60, height: 14)
                .padding(.leading, .spacingSM)
                .padding(.vertical, .spacingXS)

            // Graph area with grid
            HStack(spacing: 0) {
                // Chart area with grid lines
                ZStack {
                    // Horizontal grid lines
                    VStack(spacing: 0) {
                        ForEach(0..<yAxisTickCount, id: \.self) { index in
                            if index > 0 {
                                Spacer()
                            }
                            Rectangle()
                                .fill(gridLineColor)
                                .frame(height: 1)
                        }
                    }

                    // Vertical grid lines (dashed)
                    HStack(spacing: 0) {
                        ForEach(0..<xAxisTickCount, id: \.self) { index in
                            if index > 0 {
                                Spacer()
                            }
                            Rectangle()
                                .fill(gridLineColor)
                                .frame(width: 1)
                        }
                    }

                    // Simulated chart line
                    skeletonChartLine
                        .padding(.vertical, 20)
                }
                .padding(.trailing, 8)

                // Y-axis labels on the right
                VStack(spacing: 0) {
                    ForEach(0..<yAxisTickCount, id: \.self) { index in
                        if index > 0 {
                            Spacer()
                        }
                        RoundedRectangle(cornerRadius: 2)
                            .fill(skeletonColor)
                            .frame(width: 24, height: 12)
                    }
                }
                .frame(width: 30)
            }
            .padding(.leading, .spacingXS)

            // X-axis labels at the bottom
            HStack(spacing: 0) {
                ForEach(0..<xAxisTickCount, id: \.self) { index in
                    if index > 0 {
                        Spacer()
                    }
                    RoundedRectangle(cornerRadius: 2)
                        .fill(skeletonColor)
                        .frame(width: 24, height: 12)
                }
            }
            .padding(.leading, .spacingXS)
            .padding(.trailing, 38) // Account for Y-axis width
            .padding(.top, 8)
        }
        .frame(height: height)
        .frame(maxWidth: .infinity, minHeight: height)
        .onAppear {
            withAnimation(.easeInOut(duration: 1.2).repeatForever(autoreverses: true)) {
                isAnimating = true
            }
        }
    }

    private var skeletonColor: Color {
        theme.textSubheading.opacity(isAnimating ? 0.4 : 0.2)
    }

    private var gridLineColor: Color {
        theme.textSubheading.opacity(isAnimating ? 0.15 : 0.08)
    }

    private var skeletonChartLine: some View {
        GeometryReader { geometry in
            Path { path in
                let width = geometry.size.width
                let height = geometry.size.height
                let midY = height / 2

                path.move(to: CGPoint(x: 0, y: midY + height * 0.2))

                // Create a wavy line to simulate chart data
                let points: [(CGFloat, CGFloat)] = [
                    (0.15, 0.15),
                    (0.30, 0.10),
                    (0.45, 0.05),
                    (0.60, -0.05),
                    (0.75, -0.10),
                    (0.90, -0.15),
                    (1.0, -0.20)
                ]

                for (xRatio, yRatio) in points {
                    path.addLine(to: CGPoint(x: width * xRatio, y: midY + height * yRatio))
                }
            }
            .stroke(skeletonColor, lineWidth: 3)
        }
    }
}

#Preview {
    GraphSkeletonView()
        .padding()
}
