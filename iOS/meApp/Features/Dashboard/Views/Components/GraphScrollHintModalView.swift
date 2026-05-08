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

    /// 0 = chart shows leftmost (oldest) data, 1 = chart shows rightmost (newest) data.
    /// Starts at 1 to mirror the real graph's "snap to latest" behavior.
    @State private var scrollProgress: CGFloat = 1
    @State private var fingerOffsetRatio: CGFloat = -0.28
    @State private var fingerLiftY: CGFloat = 0
    @State private var fingerOpacity: Double = 0

    private let demoHeight: CGFloat = 180
    private let chartContentMultiplier: CGFloat = 2.6
    private let demoCornerRadius: CGFloat = .radiusLG

    /// 3 swipes that ratchet the chart back through history (older readings).
    /// Finger drags right; chart follows, exposing older data on the left.
    private let historySwipeTargets: [CGFloat] = [0.66, 0.33, 0.0]

    /// 2 swipes that return to the most recent data.
    /// Finger drags left; chart follows, snapping back to the latest entry.
    private let recentSwipeTargets: [CGFloat] = [0.5, 1.0]

    var body: some View {
        VStack(spacing: 0) {
            closeButtonRow

            animatedDemo
                .frame(height: demoHeight)
                .padding(.bottom, .spacingMD)

            Text("See your full history")
                .fontOpenSans(.heading4)
                .foregroundColor(theme.textHeading)
                .multilineTextAlignment(.center)
                .padding(.bottom, .spacingXS)

            Text("Swipe left or right on the chart to scroll through your weight history.")
                .fontOpenSans(.body2)
                .foregroundColor(theme.textSubheading)
                .multilineTextAlignment(.center)
                .padding(.bottom, .spacingMD)

            ButtonView(
                text: "Got it",
                type: .filledPrimary,
                size: .large,
                isDisabled: false,
                action: onClose
            )
        }
        .padding(.spacingMD)
        .background(theme.backgroundSecondary)
        .cornerRadius(.radiusXL)
        .shadow(color: Color.black.opacity(0.12), radius: 10, x: 0, y: 5)
        .task { await runSwipeLoop() }
    }

    // MARK: - Subviews

    private var closeButtonRow: some View {
        HStack(spacing: 0) {
            Button(action: onClose) {
                AppIconView(icon: AppAssets.xmarkSmall, size: IconSize(width: 20, height: 20))
                    .foregroundColor(theme.statusIconPrimary)
            }
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
                    .fill(theme.backgroundPrimary)

                // The chart is wider than the viewport and slides under the
                // clip, mimicking a real horizontal scroll.
                MiniChartDemoView()
                    .frame(width: chartContentWidth, height: viewportHeight - 24)
                    .offset(x: -maxScroll * scrollProgress)
                    .frame(width: viewportWidth, height: viewportHeight, alignment: .leading)
                    .clipShape(RoundedRectangle(cornerRadius: demoCornerRadius))

                edgeFades(width: viewportWidth)

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

    /// Two stacked symbols give the finger a dark outline, so the warm
    /// fill color stays readable on both light and dark chart backgrounds
    /// regardless of theme.
    private var fingerIcon: some View {
        ZStack {
            Image(systemName: "hand.point.up.left.fill")
                .font(.system(size: 42, weight: .regular))
                .foregroundColor(Color.black.opacity(0.85))

            Image(systemName: "hand.point.up.left.fill")
                .font(.system(size: 36, weight: .regular))
                .foregroundColor(theme.statusIconPrimary)
        }
        .shadow(color: Color.black.opacity(0.35), radius: 5, x: 0, y: 2)
    }

    private func edgeFades(width: CGFloat) -> some View {
        HStack(spacing: 0) {
            LinearGradient(
                colors: [theme.backgroundPrimary, theme.backgroundPrimary.opacity(0)],
                startPoint: .leading,
                endPoint: .trailing
            )
            .frame(width: 28)

            Spacer()

            LinearGradient(
                colors: [theme.backgroundPrimary.opacity(0), theme.backgroundPrimary],
                startPoint: .leading,
                endPoint: .trailing
            )
            .frame(width: 28)
        }
        .allowsHitTesting(false)
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

        for (index, target) in targets.enumerated() {
            withAnimation(.easeOut(duration: fingerDragDuration)) {
                fingerOffsetRatio = endRatio
            }
            withAnimation(.timingCurve(0.0, 0.0, 0.2, 1.0, duration: chartGlideDuration)) {
                scrollProgress = target
            }
            await sleep(seconds: chartGlideDuration)

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
            }
        }

        withAnimation(.easeOut(duration: fadeDuration)) {
            fingerOpacity = 0
        }
        await sleep(seconds: fadeDuration)
    }

    private func sleep(seconds: Double) async {
        try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
    }
}

// MARK: - Mini chart demo

private struct MiniChartDemoView: View {
    @Environment(\.appTheme) private var theme

    /// Time flows left → right (oldest → newest), and weight decreases over
    /// that time — the typical weight-loss arc users see in the real graph.
    private let points: [CGFloat] = [
        0.78, 0.62, 0.70, 0.55, 0.66, 0.48, 0.58, 0.40, 0.52,
        0.34, 0.46, 0.30, 0.42, 0.24, 0.36, 0.22, 0.32, 0.18
    ]

    var body: some View {
        GeometryReader { geo in
            let stepX = geo.size.width / CGFloat(points.count - 1)
            let chartHeight = geo.size.height
            let topInset: CGFloat = 12
            let bottomInset: CGFloat = 18
            let usable = chartHeight - topInset - bottomInset

            ZStack(alignment: .topLeading) {
                gridlines(height: chartHeight, topInset: topInset, usable: usable)

                line(stepX: stepX, topInset: topInset, usable: usable)

                dots(stepX: stepX, topInset: topInset, usable: usable)
            }
        }
    }

    private func gridlines(height: CGFloat, topInset: CGFloat, usable: CGFloat) -> some View {
        VStack(spacing: 0) {
            ForEach(0..<3, id: \.self) { i in
                Rectangle()
                    .fill(theme.textSubheading.opacity(0.12))
                    .frame(height: 1)
                    .padding(.top, i == 0 ? topInset : usable / 2 - 0.5)
                    .padding(.leading, 0)
            }
            Spacer()
        }
    }

    private func line(stepX: CGFloat, topInset: CGFloat, usable: CGFloat) -> some View {
        Path { path in
            for (i, value) in points.enumerated() {
                let x = CGFloat(i) * stepX
                let y = topInset + usable - (value * usable)
                if i == 0 {
                    path.move(to: CGPoint(x: x, y: y))
                } else {
                    path.addLine(to: CGPoint(x: x, y: y))
                }
            }
        }
        .stroke(
            theme.statusIconPrimary,
            style: StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round)
        )
    }

    private func dots(stepX: CGFloat, topInset: CGFloat, usable: CGFloat) -> some View {
        ForEach(points.indices, id: \.self) { i in
            Circle()
                .fill(theme.statusIconPrimary)
                .frame(width: 6, height: 6)
                .position(
                    x: CGFloat(i) * stepX,
                    y: topInset + usable - (points[i] * usable)
                )
        }
    }
}

// MARK: - Preview

#Preview {
    GraphScrollHintModalView(onClose: {})
        .environmentObject(Theme.shared)
        .padding()
        .background(Color.gray.opacity(0.3))
}
