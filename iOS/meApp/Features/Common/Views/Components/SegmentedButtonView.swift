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
    /// Opt-in — scales all segments together to one shared font size.
    var useUniformFontScaling: Bool = false
    /// Opt-in — sizes each segment to its intrinsic content width instead of
    /// dividing the parent equally. Required when the control is hosted in a
    /// horizontal ScrollView so it can actually scroll under Dynamic Type.
    var usesIntrinsicWidth: Bool = false
    /// Opt-in — returns a stable accessibility identifier for each segment so
    /// UI tests can tap a specific tab by name. Pass `nil` to leave segments
    /// untagged (the default).
    var accessibilityIdentifierProvider: ((T) -> String)?
    @Environment(\.appTheme) private var theme
    /// Stores the width of each segment (indexed by its position in the `segments` array).
    @State private var segmentWidths: [Int: CGFloat] = [:]
    /// Cached natural (unscaled) width of the widest label.
    /// Depends only on `segments` + base font (both static for the lifetime of this view), so
    /// computed once on appear instead of on every body access. `0` means "not yet measured" —
    /// the `uniformFontSize` getter handles that case the same way it always did via the
    /// existing `widestLabelWidth > 0` guard.
    @State private var cachedWidestLabelWidth: CGFloat = 0

    /// heading5 size — used only when `useUniformFontScaling` is on.
    private static var baseFontSize: CGFloat { 16 }
    /// Horizontal padding (left + right) applied inside each segment button in uniform mode.
    private static var buttonHorizontalPadding: CGFloat { 16 }

    var body: some View {
        HStack(spacing: 0) {
            ForEach(Array(segments.enumerated()), id: \.element) { index, segment in
                Button(action: {
                    withAnimation(.spring(response: 0.45, dampingFraction: 0.85, blendDuration: 0)) {
                        selectedSegment = segment
                    }
                }, label: {
                    Text(segmentDisplayName(for: segment))
                        .fontOpenSans(.heading5)
                        .foregroundColor(selectedSegment == segment ? theme.textInverse : theme.actionTertiary)
                        .frame(maxWidth: usesIntrinsicWidth ? nil : .infinity)
                        .lineLimit(1)
                        .padding(.vertical, 8)
                        .padding(.horizontal, useUniformFontScaling ? 8 : 12)
                        .background(
                            GeometryReader { geometry in
                                Color.clear
                                    .preference(
                                        key: SegmentWidthPreferenceKey.self,
                                        value: [index: geometry.size.width]
                                    )
                            }
                        )
                })
                .zIndex(1)
                .id(segment)
                .accessibilityIdentifier(accessibilityIdentifierProvider?(segment) ?? "")
                // Exposes active segment as `selected` for E2E (MOB-399). Metadata-only.
                .accessibilityAddTraits(selectedSegment == segment ? .isSelected : [])
            }
        }
        .onPreferenceChange(SegmentWidthPreferenceKey.self) { newWidths in
            // Short-circuit when widths haven't changed. Without this guard,
            // `onPreferenceChange` fires on every dashboard body recompute, mutates
            // `segmentWidths`, and re-invalidates the body — a feedback loop that
            // produced 4 severe (≥500 ms) hangs at Thermal Nominal during tab
            // switches and scroll. See `iOS/docs/dashboard-graph-hang-fix-plan.md`
            // §Step 6 / history doc §3.10.
            guard newWidths != segmentWidths else { return }
            segmentWidths.merge(newWidths) { _, new in new }
        }
        .onAppear {
            // Compute the widest-label width once per session (depends only on
            // `segments` + a static font), instead of on every `uniformFontSize`
            // read. See history doc §3.10.3 for trace evidence.
            if cachedWidestLabelWidth == 0 {
                cachedWidestLabelWidth = computeWidestLabelWidth()
            }
        }
        .background(
            // Animated background
            RoundedRectangle(cornerRadius: .radiusMD)
                .fill(theme.actionPrimary)
                .frame(width: selectedWidth())
                .offset(x: calculateOffset())
                .compositingGroup()
        )
        .background(theme.backgroundSecondary)
        .clipShape(RoundedRectangle(cornerRadius: .radiusMD))
    }

    /// Renders the label with the uniform-scaling font when opted in, otherwise the default heading5.
    @ViewBuilder
    private func labelText(for segment: T) -> some View {
        if useUniformFontScaling {
            Text(segmentDisplayName(for: segment))
                .font(.custom("OpenSans-Regular", size: uniformFontSize))
                .fontWeight(.bold)
                .allowsTightening(true)
        } else {
            Text(segmentDisplayName(for: segment))
                .fontOpenSans(.heading5)
                .minimumScaleFactor(0.8)
        }
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
    /// Called from `.onAppear` to populate `cachedWidestLabelWidth`. Don't read
    /// directly on the hot path — read `cachedWidestLabelWidth` instead.
    private func computeWidestLabelWidth() -> CGFloat {
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
              cachedWidestLabelWidth > 0 else { return Self.baseFontSize }
        let contentWidth = minSegmentWidth - Self.buttonHorizontalPadding
        guard contentWidth > 0 else { return Self.baseFontSize }
        let ratio = contentWidth / cachedWidestLabelWidth
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

private struct SegmentWidthPreferenceKey: PreferenceKey {
    static let defaultValue: [Int: CGFloat] = [:]
    static func reduce(value: inout [Int: CGFloat], nextValue: () -> [Int: CGFloat]) {
        value.merge(nextValue()) { _, new in new }
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
