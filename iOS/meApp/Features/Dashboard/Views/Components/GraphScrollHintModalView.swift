//
//  GraphScrollHintModalView.swift
//  meApp
//
//  First-time discoverability hint that teaches users the weight-trend graph
//  is scrollable. Designed to be shown via NotificationHelperService.showModal.
//

import SwiftUI

struct GraphScrollHintModalView: View {
    let onClose: () -> Void

    @Environment(\.appTheme) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// 0 = chart shows leftmost (oldest) data, 1 = chart shows rightmost (newest) data.
    /// Starts at 1 to mirror the real graph's "snap to latest" behavior.
    @State private var scrollProgress: CGFloat = 1
    @State private var fingerOffsetRatio: CGFloat = -0.28
    @State private var fingerLiftY: CGFloat = 0
    @State private var fingerOpacity: Double = 0

    /// Aspect ratio (W:H) of the chart card in the Figma design (212 × 153).
    /// Driving size from the ratio — instead of a fixed height — keeps the
    /// card from looking chunky/tall when the popup stretches wider.
    private let demoAspectRatio: CGFloat = 212.0 / 153.0
    private let chartContentMultiplier: CGFloat = 2.6
    private let demoCornerRadius: CGFloat = .radiusSM

    /// 3 swipes that ratchet the chart back through history (older readings).
    /// Finger drags right; chart follows, exposing older data on the left.
    private let historySwipeTargets: [CGFloat] = [0.66, 0.33, 0.0]

    /// 2 swipes that return to the most recent data.
    /// Finger drags left; chart follows, snapping back to the latest entry.
    private let recentSwipeTargets: [CGFloat] = [0.5, 1.0]

    var body: some View {
        VStack(spacing: 0) {
            closeButtonRow

            VStack(spacing: .spacingLG) {
                VStack(spacing: .spacingSM) {
                    animatedDemo
                        .aspectRatio(demoAspectRatio, contentMode: .fit)
                        .padding(.horizontal, .spacingMD)
                        .accessibilityHidden(true)

                    VStack(spacing: .spacingSM) {
                        Text(DashboardStrings.graphScrollHintTitle)
                            .fontOpenSans(.heading4)
                            .foregroundColor(theme.textHeading)
                            .multilineTextAlignment(.center)

                        Text(DashboardStrings.graphScrollHintMessage)
                            .fontOpenSans(.body2)
                            .foregroundColor(theme.textBody)
                            .multilineTextAlignment(.center)
                    }
                }

                ButtonView(
                    text: DashboardStrings.graphScrollHintConfirm,
                    type: .filledPrimary,
                    size: .large,
                    isDisabled: false,
                    action: onClose
                )
                .appAccessibility(id: AccessibilityID.graphScrollHintConfirmButton)
            }
        }
        .padding(.spacingMD)
        .background(theme.backgroundPrimary)
        .cornerRadius(.radiusXL)
        .screenAccessibilityRoot(AccessibilityID.graphScrollHintModalRoot)
        .shadow(color: Color.black.opacity(0.12), radius: 10, x: 0, y: 5)
        .task {
            guard !reduceMotion else { return }
            await runSwipeLoop()
        }
    }

    // MARK: - Subviews

    private var closeButtonRow: some View {
        HStack(spacing: 0) {
            Button(action: onClose) {
                AppIconView(icon: AppAssets.xmarkSmall, size: IconSize(width: 20, height: 20))
                    .foregroundColor(theme.actionPrimary)
            }
            .appAccessibility(id: AccessibilityID.graphScrollHintCloseButton)
            .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .padding(.bottom, .spacingXS)
    }

    private var animatedDemo: some View {
        GeometryReader { geo in
            let viewportWidth = geo.size.width
            let viewportHeight = geo.size.height
            let chartContentWidth = viewportWidth * chartContentMultiplier
            let maxScroll = chartContentWidth - viewportWidth

            ZStack {
                RoundedRectangle(cornerRadius: demoCornerRadius)
                    .fill(theme.backgroundSecondary)

                // The chart is wider than the viewport and slides under the
                // clip, mimicking a real horizontal scroll.
                MiniChartDemoView()
                    .frame(width: chartContentWidth, height: viewportHeight)
                    .offset(x: -maxScroll * scrollProgress)
                    .frame(width: viewportWidth, height: viewportHeight, alignment: .leading)
                    .clipShape(RoundedRectangle(cornerRadius: demoCornerRadius))

                // Finger appears at the swipe start, drags the chart, then
                // lifts (fades out) — a single discrete gesture per cycle.
                fingerIcon
                    .opacity(fingerOpacity)
                    .offset(
                        x: viewportWidth * fingerOffsetRatio,
                        y: viewportHeight * 0.18 + fingerLiftY
                    )
            }
            .clipShape(RoundedRectangle(cornerRadius: demoCornerRadius))
        }
    }

    /// Card-fill + blue-stroke finger, matching the Figma. The filled glyph
    /// uses the chart card's own background color so the interior reads as
    /// part of the card, with the outline version on top supplying a crisp
    /// brand-colored stroke.
    private var fingerIcon: some View {
        ZStack {
            Image(systemName: "hand.point.up.left.fill")
                .resizable()
                .aspectRatio(contentMode: .fit)
                .frame(width: 34, height: 34)
                .foregroundStyle(theme.backgroundSecondary)

            Image(systemName: "hand.point.up.left")
                .resizable()
                .aspectRatio(contentMode: .fit)
                .frame(width: 34, height: 34)
                .foregroundStyle(theme.actionPrimary)
        }
    }

    // MARK: - Animation

    @MainActor
    private func runSwipeLoop() async {
        scrollProgress = 1
        fingerOpacity = 0
        fingerLiftY = 0

        while !Task.isCancelled {
            // History dive: finger drags right, chart slides right, older
            // entries enter from the left.
            await performSwipeBatch(targets: historySwipeTargets, goingLeft: false)
            if Task.isCancelled { return }
            await sleep(seconds: 0.65)

            // Return to recent: finger drags left, chart snaps back forward.
            await performSwipeBatch(targets: recentSwipeTargets, goingLeft: true)
            if Task.isCancelled { return }
            await sleep(seconds: 0.65)
        }
    }

    /// Plays a series of same-direction swipes with the finger staying
    /// visible the whole time. Between swipes the finger "lifts" (rises a
    /// few points and slides back to start) instead of fading out, which
    /// kills the blink-on-blink-off feel.
    @MainActor
    private func performSwipeBatch(targets: [CGFloat], goingLeft: Bool) async {
        let fingerDragDuration = 0.32
        let chartGlideDuration = 0.7
        let fadeDuration = 0.18
        let startRatio: CGFloat = goingLeft ? 0.28 : -0.28
        let endRatio: CGFloat = goingLeft ? -0.12 : 0.12

        fingerOffsetRatio = startRatio
        fingerLiftY = 0

        withAnimation(.easeIn(duration: fadeDuration)) {
            fingerOpacity = 1
        }
        await sleep(seconds: fadeDuration)
        if Task.isCancelled { return }

        for (index, target) in targets.enumerated() {
            withAnimation(.easeOut(duration: fingerDragDuration)) {
                fingerOffsetRatio = endRatio
            }
            withAnimation(.timingCurve(0.0, 0.0, 0.2, 1.0, duration: chartGlideDuration)) {
                scrollProgress = target
            }
            await sleep(seconds: chartGlideDuration)
            if Task.isCancelled { return }

            // Reposition for the next swipe: lift up, slide back, set down.
            // Chart stays put — only the finger moves.
            if index < targets.count - 1 {
                withAnimation(.easeOut(duration: 0.14)) {
                    fingerLiftY = -14
                }
                await sleep(seconds: 0.10)
                withAnimation(.easeInOut(duration: 0.24)) {
                    fingerOffsetRatio = startRatio
                }
                await sleep(seconds: 0.20)
                withAnimation(.easeIn(duration: 0.14)) {
                    fingerLiftY = 0
                }
                await sleep(seconds: 0.14)
                if Task.isCancelled { return }
            }
        }

        withAnimation(.easeOut(duration: fadeDuration)) {
            fingerOpacity = 0
        }
        await sleep(seconds: fadeDuration)
    }

    private func sleep(seconds: Double) async {
        try? await Task.sleep(for: .seconds(seconds))
    }
}

// MARK: - Mini chart demo

private struct MiniChartDemoView: View {
    @Environment(\.appTheme) private var theme

    /// Time flows left → right (oldest → newest), and weight trends down over
    /// that time — the typical weight-loss arc users see in the real graph.
    private let points: [CGFloat] = [
        0.78, 0.62, 0.70, 0.55, 0.66, 0.48, 0.58, 0.40, 0.52,
        0.34, 0.46, 0.30, 0.42, 0.24, 0.36, 0.22, 0.32, 0.18
    ]

    var body: some View {
        GeometryReader { geo in
            let stepX = geo.size.width / CGFloat(points.count - 1)
            let chartHeight = geo.size.height
            let topInset: CGFloat = 8
            let bottomInset: CGFloat = 8
            let usable = chartHeight - topInset - bottomInset

            ZStack(alignment: .topLeading) {
                gridlines(height: chartHeight)

                line(stepX: stepX, topInset: topInset, usable: usable)

                dots(stepX: stepX, topInset: topInset, usable: usable)
            }
        }
    }

    /// Three evenly-spaced interior gridlines. Top and bottom edges are
    /// intentionally bare — gridlines flush with the card's rounded
    /// corners read as a border instead of a chart axis.
    private func gridlines(height: CGFloat) -> some View {
        let lineColor = theme.textSubheading.opacity(0.35)
        return VStack(spacing: 0) {
            Spacer(minLength: 0)
            Rectangle().fill(lineColor).frame(height: 1)
            Spacer(minLength: 0)
            Rectangle().fill(lineColor).frame(height: 1)
            Spacer(minLength: 0)
            Rectangle().fill(lineColor).frame(height: 1)
            Spacer(minLength: 0)
        }
        .frame(height: height)
    }

    /// Gently smoothed polyline: each segment is a cubic Bézier whose control
    /// points sit just slightly off the straight line between samples
    /// (`smoothing` < 1 dampens the full Catmull-Rom curvature). The result
    /// rounds the corners without turning the chart into a wavy curve.
    private func line(stepX: CGFloat, topInset: CGFloat, usable: CGFloat) -> some View {
        Path { path in
            guard points.count > 1 else { return }
            let smoothing: CGFloat = 0.35
            let cgPoints: [CGPoint] = points.enumerated().map { i, value in
                CGPoint(x: CGFloat(i) * stepX, y: topInset + usable - (value * usable))
            }
            path.move(to: cgPoints[0])
            for i in 1..<cgPoints.count {
                let p0 = cgPoints[max(i - 2, 0)]
                let p1 = cgPoints[i - 1]
                let p2 = cgPoints[i]
                let p3 = cgPoints[min(i + 1, cgPoints.count - 1)]
                let c1 = CGPoint(
                    x: p1.x + (p2.x - p0.x) * smoothing / 3,
                    y: p1.y + (p2.y - p0.y) * smoothing / 3
                )
                let c2 = CGPoint(
                    x: p2.x - (p3.x - p1.x) * smoothing / 3,
                    y: p2.y - (p3.y - p1.y) * smoothing / 3
                )
                path.addCurve(to: p2, control1: c1, control2: c2)
            }
        }
        .stroke(
            theme.actionPrimary,
            style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round)
        )
    }

    private func dots(stepX: CGFloat, topInset: CGFloat, usable: CGFloat) -> some View {
        ForEach(points.indices, id: \.self) { i in
            Circle()
                .fill(theme.actionPrimary)
                .frame(width: 5, height: 5)
                .position(
                    x: CGFloat(i) * stepX,
                    y: topInset + usable - (points[i] * usable)
                )
        }
    }
}

// MARK: - Preview

#Preview {
    GraphScrollHintModalView {}
        .environmentObject(Theme.shared)
        .padding()
        .background(Color.gray.opacity(0.3))
}
