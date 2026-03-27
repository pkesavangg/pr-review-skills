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
    
    private var displayValues: BpmDisplayData? {
        dashboardStore.displayManager?.getBpmDisplayValues()
    }
    
    /// Reuses the same label logic as weight: "no entries", "week average", "day average", etc.
    private var displayLabel: String {
        dashboardStore.displayManager?.weightDisplayLabel ?? BpmDashboardStrings.noEntries
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
                            .overlay(alignment: .topTrailing) {
                                Button {
                                    showAhaRatingSheet = true
                                } label: {
                                    AppIconView(icon: AppAssets.helpCircle, size: IconSize(width: 16, height: 16))
                                        .foregroundColor(theme.textSubheading)
                                        .padding(.top, -4)      // tweak to align visually
                                        .padding(.trailing, -6) // tweak to sit nicely at edge
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        
                        VStack(alignment: .leading, spacing: 0) {
                            Text(BpmDashboardStrings.pulse)
                                .fontOpenSans(.subHeading2)
                                .foregroundColor(theme.textSubheading)
                            
                            Text("\(values.pulse)")
                                .fontWeight(.heavy)
                                .fontOpenSans(.heading1)
                                .foregroundColor(theme.textSubheading)
                                .lineLimit(1)
                                .fixedSize(horizontal: true, vertical: false)
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
            return BpmDashboardStrings.bpReadingAccessibility(systolic: data.systolic, diastolic: data.diastolic, pulse: data.pulse, label: data.classification.label)
        }())
        .sheet(isPresented: $showAhaRatingSheet) {
            AhaRatingSheet()
        }
    }
}
