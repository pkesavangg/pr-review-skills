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
        static let pulseColumnWidth: CGFloat = 88
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
                                    Text("\(values.systolic)")
                                        .fontWeight(.heavy)
                                        .fontOpenSans(.heading1)
                                        .foregroundColor(values.classification.color(theme: theme))
                                        .lineLimit(1)
                                        .fixedSize()

                                    SlashDividerView(
                                        color: theme.textSubheading.opacity(0.45)
                                    )

                                    Text("\(values.diastolic)")
                                        .fontWeight(.heavy)
                                        .fontOpenSans(.heading1)
                                        .foregroundColor(values.classification.color(theme: theme))
                                        .lineLimit(1)
                                        .fixedSize()
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

                                Text("\(values.pulse)")
                                    .fontWeight(.heavy)
                                    .fontOpenSans(.heading1)
                                    .foregroundColor(theme.textSubheading)
                                    .lineLimit(1)
                                    .fixedSize(horizontal: true, vertical: false)
                                    .opacity(isGraphLoading ? 0 : 1)
                            }
                            .animation(.easeInOut(duration: 0.3), value: dashboardStore.state.graph.isGraphReady)
                        }
                        .frame(width: Layout.pulseColumnWidth, alignment: .leading)
                    }
                }
                .padding(.horizontal, Layout.horizontalPadding)
            } else {
                HStack(alignment: .lastTextBaseline, spacing: Layout.unitSpacing) {
                    Text(BpmDashboardStrings.bpPlaceholder)
                        .fontWeight(.heavy)
                        .fontOpenSans(.heading1)
                        .foregroundColor(theme.textSubheading)
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
