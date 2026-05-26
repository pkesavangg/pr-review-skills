import SwiftUI

/// A `Text`-backed view whose `Equatable` conformance lets SwiftUI short-circuit
/// the body recompute (and the underlying Core Text resolution) when the visible
/// string and styling haven't changed.
///
/// Use everywhere on the dashboard hot path: chart axis tick labels, weight
/// labels, segmented-control labels. Wrap the call site with `.equatable()`
/// so SwiftUI applies the `==` short-circuit.
///
/// Note: `Color` is `Equatable` on iOS 17+; the app's deployment target is
/// well beyond that.
struct CachedLabel: View, Equatable {
    let text: String
    let font: Font
    let color: Color

    init(_ text: String,
         font: Font = .body,
         color: Color = .primary) {
        self.text = text
        self.font = font
        self.color = color
    }

    var body: some View {
        Text(text)
            .font(font)
            .foregroundColor(color)
    }
}

/// A specialized cached label for the chart's X-axis ticks. Captures only the
/// inputs that actually affect the rendered glyph (text + color); style
/// modifiers (`fixedSize`, `padding`, `background`) are applied by the caller.
struct CachedXAxisLabel: View, Equatable {
    let text: String
    let color: Color

    var body: some View {
        Text(text)
            .font(.caption)
            .foregroundColor(color)
    }
}

/// A specialized cached label for the chart's Y-axis ticks. Captures the full
/// modifier set the previous inline `Text` chain applied so behavior is
/// preserved exactly.
struct CachedYAxisLabel: View, Equatable {
    let text: String
    let color: Color
    let width: CGFloat
    let isVisible: Bool

    var body: some View {
        Text(text)
            .fontOpenSans(.subHeading2)
            .multilineTextAlignment(.leading)
            .fontWeight(.regular)
            .monospacedDigit()
            .foregroundColor(color)
            .frame(width: width, alignment: .center)
            .opacity(isVisible ? 1 : 0)
    }
}
