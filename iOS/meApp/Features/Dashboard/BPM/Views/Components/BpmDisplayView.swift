//
//  BpmDisplayView.swift
//  meApp
//
//  Shows the systolic/diastolic headline and pulse, colored by AHA classification.
//  Layout mirrors weightInfoSection + WeightDisplayView for consistent spacing.
//

import SwiftUI

struct BpmDisplayView: View {
    private enum Layout {
        static let helpButtonTopPadding: CGFloat = 6
        static let helpButtonHorizontalPadding: CGFloat = 4
        static let horizontalPadding: CGFloat = 14
        static let contentSpacing: CGFloat = 2
        static let valueSpacing: CGFloat = 12
        static let unitSpacing: CGFloat = 4
        static let slashSpacing: CGFloat = 12
        static let pulseColumnWidth: CGFloat = 108
        /// Shrink-to-fit floor for BP/pulse values so a 3-digit worst case
        /// (e.g. 155/100 + pulse 100) scales down instead of clipping off-screen.
        static let valueMinScale: CGFloat = 0.5
    }
    
    @ObservedObject var dashboardStore: DashboardStore
    @Environment(\.appTheme) private var theme
    @State private var showAhaRatingSheet = false
    @State private var isSkeletonAnimating = false

    private var displayValues: BpmDisplayData? {
        dashboardStore.displayManager?.getBpmDisplayValues()
    }

    /// Reuses the same label logic as weight: "no entries", "week average", "day average", etc.
    private var displayLabel: String {
        dashboardStore.displayManager?.weightDisplayLabel ?? BpmDashboardStrings.noEntries
    }

    private var isGraphLoading: Bool {
        !dashboardStore.state.graph.isGraphReady
    }

    private var skeletonColor: Color {
        theme.textSubheading.opacity(isSkeletonAnimating ? 0.4 : 0.2)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: .zero) {
            if let values = displayValues {
                VStack(alignment: .leading, spacing: Layout.contentSpacing) {
                    HStack(alignment: .top, spacing: Layout.valueSpacing) {
                        VStack(alignment: .leading, spacing: 0) {
                            Text(BpmDashboardStrings.mmhg)
                                .fontOpenSans(.subHeading2)
                                .foregroundColor(theme.textSubheading)

                            ZStack(alignment: .leading) {
                                if isGraphLoading {
                                    bpValueSkeleton
                                }

                                HStack(alignment: .lastTextBaseline, spacing: Layout.slashSpacing) {
                                    bpNumber(
                                        "\(values.systolic)",
                                        color: values.classification.color(theme: theme),
                                        alignment: .trailing
                                    )

                                    SlashDividerView(
                                        color: theme.textSubheading.opacity(0.45)
                                    )

                                    bpNumber(
                                        "\(values.diastolic)",
                                        color: values.classification.color(theme: theme),
                                        alignment: .leading
                                    )
                                }
                                .opacity(isGraphLoading ? 0 : 1)
                                .overlay(alignment: .topTrailing) {
                                    Button {
                                        showAhaRatingSheet = true
                                    } label: {
                                        AppIconView(icon: AppAssets.helpCircle, size: IconSize(width: 16, height: 16))
                                            .foregroundColor(theme.textSubheading)
                                            .padding(.top, -4)
                                            .padding(.trailing, -6)
                                    }
                                    .buttonStyle(.plain)
                                    .appAccessibility(id: AccessibilityID.bpmAhaHelpButton)
                                    .opacity(isGraphLoading ? 0 : 1)
                                }
                            }
                            .animation(.easeInOut(duration: 0.3), value: dashboardStore.state.graph.isGraphReady)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)

                        VStack(alignment: .leading, spacing: 0) {
                            Text(BpmDashboardStrings.pulse)
                                .fontOpenSans(.subHeading2)
                                .foregroundColor(theme.textSubheading)

                            ZStack(alignment: .leading) {
                                if isGraphLoading {
                                    pulseValueSkeleton
                                }

                                // MOB-1591: heading2 (50pt), not heading1 (60pt). The BP header is the only
                                // dashboard header that shows THREE big numbers on one row (sys/dia + pulse);
                                // at 60pt the 3-digit worst case (155/100 + pulse 100) overflows off-screen
                                // on large phones. Step every value down to heading2 so the whole row fits.
                                // (Baby does the same in BabyTrendView when it has two values.)
                                Text("\(values.pulse)")
                                    .fontWeight(.heavy)
                                    .fontOpenSans(.heading2)
                                    .foregroundColor(theme.textSubheading)
                                    .lineLimit(1)
                                    .minimumScaleFactor(Layout.valueMinScale)
                                    .opacity(isGraphLoading ? 0 : 1)
                            }
                            .animation(.easeInOut(duration: 0.3), value: dashboardStore.state.graph.isGraphReady)
                        }
                        .frame(width: Layout.pulseColumnWidth, alignment: .leading)
                    }
                }
                .padding(.horizontal, Layout.horizontalPadding)
            } else {
                VStack(alignment: .leading, spacing: Layout.contentSpacing) {
                    HStack(alignment: .top, spacing: Layout.valueSpacing) {
                        VStack(alignment: .leading, spacing: 0) {
                            Text(BpmDashboardStrings.mmhg)
                                .fontOpenSans(.subHeading2)
                                .foregroundColor(theme.textSubheading)

                            HStack(alignment: .lastTextBaseline, spacing: Layout.slashSpacing) {
                                // MOB-1591: heading2 step-down — keep the empty placeholder in sync with the
                                // populated header (see the note in bpNumber).
                                Text(BpmDashboardStrings.bpSystolicZeroPlaceholder)
                                    .fontWeight(.heavy)
                                    .fontOpenSans(.heading2)
                                    .foregroundColor(theme.textSubheading)
                                    .lineLimit(1)
                                    .minimumScaleFactor(Layout.valueMinScale)

                                SlashDividerView(color: theme.textSubheading.opacity(0.45))

                                // MOB-1591: heading2 step-down (see bpNumber note).
                                Text(BpmDashboardStrings.bpDiastolicZeroPlaceholder)
                                    .fontWeight(.heavy)
                                    .fontOpenSans(.heading2)
                                    .foregroundColor(theme.textSubheading)
                                    .lineLimit(1)
                                    .minimumScaleFactor(Layout.valueMinScale)
                            }
                            .overlay(alignment: .topTrailing) {
                                Button {
                                    showAhaRatingSheet = true
                                } label: {
                                    AppIconView(icon: AppAssets.helpCircle, size: IconSize(width: 16, height: 16))
                                        .foregroundColor(theme.textSubheading)
                                        .padding(.top, -4)
                                        .padding(.trailing, -6)
                                }
                                .buttonStyle(.plain)
                                .appAccessibility(id: AccessibilityID.bpmAhaHelpButton)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)

                        VStack(alignment: .leading, spacing: 0) {
                            Text(BpmDashboardStrings.pulse)
                                .fontOpenSans(.subHeading2)
                                .foregroundColor(theme.textSubheading)

                            // MOB-1591: heading2 step-down (see bpNumber note).
                            Text(BpmDashboardStrings.bpPulseZeroPlaceholder)
                                .fontWeight(.heavy)
                                .fontOpenSans(.heading2)
                                .foregroundColor(theme.textSubheading)
                                .lineLimit(1)
                                .minimumScaleFactor(Layout.valueMinScale)
                        }
                        .frame(width: Layout.pulseColumnWidth, alignment: .leading)
                    }
                }
                .padding(.horizontal, Layout.horizontalPadding)
            }

        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel({
            guard let data = displayValues else { return BpmDashboardStrings.noBloodPressureData }
            return BpmDashboardStrings.bpReadingAccessibility(
                systolic: data.systolic,
                diastolic: data.diastolic,
                pulse: data.pulse,
                label: data.classification.label
            )
        }())
        .sheet(isPresented: $showAhaRatingSheet) {
            AhaRatingSheet()
        }
    }

    // MARK: - Fixed-width BP number

    /// A blood-pressure value rendered in a FIXED 3-digit-wide slot, so selecting different readings
    /// (e.g. 150/100 vs 140/93) never reflows the header — the slash, the AHA help button, and the pulse
    /// column all stay put. A hidden "000" ghost (same heavy heading font, monospaced digits) reserves the
    /// widest BP width; the real value floats inside it, hugging the slash (systolic trailing, diastolic
    /// leading). The slot equals today's widest case (3 digits), so nothing gets wider — smaller values just
    /// stop shrinking the block.
    /// MOB-1591: rendered at heading2 (50pt), not heading1 (60pt). Three big numbers share this row
    /// (sys/dia + pulse); at 60pt the 3-digit worst case overflows off-screen on large phones. The ghost
    /// "000" MUST use the same style as the real value so the reserved 3-digit width matches what's drawn.
    private func bpNumber(_ text: String, color: Color, alignment: Alignment) -> some View {
        Text(verbatim: "000")
            .fontWeight(.heavy)
            .fontOpenSans(.heading2)
            .monospacedDigit()
            .hidden()
            .overlay(alignment: alignment) {
                Text(text)
                    .fontWeight(.heavy)
                    .fontOpenSans(.heading2)
                    .monospacedDigit()
                    .foregroundColor(color)
                    .lineLimit(1)
                    .minimumScaleFactor(Layout.valueMinScale)
            }
    }

    // MARK: - Skeletons

    private var bpValueSkeleton: some View {
        HStack(spacing: Layout.slashSpacing) {
            RoundedRectangle(cornerRadius: 6)
                .fill(skeletonColor)
                .frame(width: 60, height: 40)
            RoundedRectangle(cornerRadius: 6)
                .fill(skeletonColor)
                .frame(width: 60, height: 40)
        }
        .frame(height: 55, alignment: .leading)
        .onAppear {
            withAnimation(.easeInOut(duration: 1.2).repeatForever(autoreverses: true)) {
                isSkeletonAnimating = true
            }
        }
    }

    private var pulseValueSkeleton: some View {
        RoundedRectangle(cornerRadius: 6)
            .fill(skeletonColor)
            .frame(width: 50, height: 40)
            .frame(height: 55, alignment: .leading)
    }
}
