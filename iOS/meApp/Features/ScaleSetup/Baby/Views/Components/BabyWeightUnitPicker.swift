//
//  BabyWeightUnitPicker.swift
//  meApp
//

import SwiftUI

/// Capsule-style segmented picker for baby weight units (lbs/oz, lb, kg).
/// Uses a sliding background capsule animation matching the standard SegmentedButtonView.
struct BabyWeightUnitPicker: View {
    let segments: [BabyWeightUnit]
    @Binding var selectedSegment: BabyWeightUnit
    @Environment(\.appTheme) private var theme
    @State private var segmentWidths: [Int: CGFloat] = [:]

    var body: some View {
        HStack(spacing: 0) {
            ForEach(Array(segments.enumerated()), id: \.element) { index, segment in
                Button {
                    withAnimation(.spring(response: 0.45, dampingFraction: 0.85, blendDuration: 0)) {
                        selectedSegment = segment
                    }
                } label: {
                    Text(segment.displayName)
                        .fontOpenSans(.button2)
                        .foregroundColor(selectedSegment == segment ? theme.textInverse : theme.actionTertiary)
                        .frame(maxWidth: .infinity)
                        .frame(minWidth: 75)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                        .padding(.horizontal, .spacingSM)
                        .padding(.vertical, .spacingXS)
                        .background(
                            GeometryReader { geometry in
                                Color.clear
                                    .onAppear {
                                        segmentWidths[index] = geometry.size.width
                                    }
                            }
                        )
                }
                .zIndex(1)
                .buttonStyle(.plain)
                .contentShape(Capsule())
                .id(segment)
            }
        }
        .background(
            Capsule()
                .fill(theme.actionPrimary)
                .frame(width: selectedWidth())
                .offset(x: calculateOffset())
                .compositingGroup()
        )
        .background(theme.backgroundPrimary)
        .clipShape(Capsule())
    }

    private func calculateOffset() -> CGFloat {
        guard
            let selectedIndex = segments.firstIndex(where: { $0.id == selectedSegment.id }),
            !segmentWidths.isEmpty
        else { return 0 }

        let totalWidth = segmentWidths.values.reduce(0, +)
        let precedingWidth = (0..<selectedIndex).reduce(CGFloat(0)) { $0 + (segmentWidths[$1] ?? 0) }
        let selectedWidth = segmentWidths[selectedIndex] ?? 0

        return -totalWidth / 2 + precedingWidth + (selectedWidth / 2)
    }

    private func selectedWidth() -> CGFloat {
        guard let index = segments.firstIndex(where: { $0.id == selectedSegment.id }) else { return 0 }
        return segmentWidths[index] ?? 0
    }
}
