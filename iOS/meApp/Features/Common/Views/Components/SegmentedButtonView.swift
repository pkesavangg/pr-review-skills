//
//  SegmentedPickerView.swift
//  meApp
//
//  Created by Lakshmi Priya on 09/06/25.
//
import SwiftUI

struct SegmentedButtonView<T: CaseIterable & RawRepresentable & Identifiable & Hashable>: View where T.RawValue == String {
    let segments: [T]
    @Binding var selectedSegment: T
    @Environment(\.appTheme) private var theme
    /// Stores the width of each segment (indexed by its position in the `segments` array).
    @State private var segmentWidths: [Int: CGFloat] = [:]

    /// heading5 size — every label shares one font size decision.
    private static var baseFontSize: CGFloat { 16 }
    /// Horizontal padding (left + right) applied inside each segment button.
    private static var buttonHorizontalPadding: CGFloat { 16 }

    var body: some View {
        HStack(spacing: 0) {
            ForEach(Array(segments.enumerated()), id: \.element) { index, segment in
                Button(action: {
                    withAnimation(.spring(response: 0.45, dampingFraction: 0.85, blendDuration: 0)) {
                        selectedSegment = segment
                    }
                }) {
                    Text(segmentDisplayName(for: segment))
                        .font(.custom("OpenSans-Regular", size: uniformFontSize))
                        .fontWeight(.bold)
                        .foregroundColor(selectedSegment == segment ? theme.textInverse : theme.actionSecondary)
                        .frame(maxWidth: .infinity)
                        .lineLimit(1)
                        .allowsTightening(true)
                        .padding(.vertical, 8)
                        .padding(.horizontal, 8)
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
                .id(segment)
            }
        }
        .background(
            // Animated background
            RoundedRectangle(cornerRadius: .radiusMD)
                .fill(theme.actionSecondary)
                .frame(width: selectedWidth())
                .offset(x: calculateOffset())
                .compositingGroup()
        )
        .clipShape(RoundedRectangle(cornerRadius: .radiusMD))
    }
    
    /// Returns the display name for a segment based on type
    private func segmentDisplayName(for segment: T) -> String {
        // Type-specific handling
        if let bodyMetric = segment as? BodyMetric {
            return bodyMetric.displayName.uppercased()
        }

        // Fallback for other types
        return segment.rawValue.uppercased()
    }

    /// Natural (unscaled) width of the widest label at the base font size.
    private var widestLabelWidth: CGFloat {
        let font = UIFont(name: "OpenSans-Bold", size: Self.baseFontSize)
            ?? .systemFont(ofSize: Self.baseFontSize, weight: .bold)
        return segments
            .map { (segmentDisplayName(for: $0) as NSString).size(withAttributes: [.font: font]).width }
            .max() ?? 0
    }

    /// Single font size applied to every segment so they scale together.
    /// If the widest label exceeds available width, all four shrink proportionally (floor 0.7×).
    private var uniformFontSize: CGFloat {
        guard let minSegmentWidth = segmentWidths.values.min(),
              minSegmentWidth > 0,
              widestLabelWidth > 0 else { return Self.baseFontSize }
        let contentWidth = minSegmentWidth - Self.buttonHorizontalPadding
        guard contentWidth > 0 else { return Self.baseFontSize }
        let ratio = contentWidth / widestLabelWidth
        if ratio >= 1.0 { return Self.baseFontSize }
        return max(Self.baseFontSize * 0.7, Self.baseFontSize * ratio)
    }

    /// Calculates the x-offset required to place the highlight behind the selected segment.
    private func calculateOffset() -> CGFloat {
        guard
            let selectedIndex = segments.firstIndex(where: { $0.id == selectedSegment.id }),
            !segmentWidths.isEmpty
        else { return 0 }

        let totalWidth = segmentWidths.values.reduce(0, +)
        let precedingWidth = (0..<selectedIndex).reduce(CGFloat(0)) { $0 + (segmentWidths[$1] ?? 0) }
        let selectedWidth = segmentWidths[selectedIndex] ?? 0

        // Start from the leading edge (-totalWidth/2), then move past the widths before the selected
        // segment and finally centre the highlight under the selected segment.
        return -totalWidth / 2 + precedingWidth + (selectedWidth / 2)
    }

    /// Returns the width of the currently selected segment (or zero until measured).
    private func selectedWidth() -> CGFloat {
        guard let index = segments.firstIndex(where: { $0.id == selectedSegment.id }) else { return 0 }
        return segmentWidths[index] ?? 0
    }
}

// MARK: - Preview
#Preview(body: {
    @Previewable
    @State var selectedSegment: GoalTypeSegment = .loseGain
    SegmentedButtonView(
        segments: GoalTypeSegment.allCases,
        selectedSegment: $selectedSegment
    )
})
