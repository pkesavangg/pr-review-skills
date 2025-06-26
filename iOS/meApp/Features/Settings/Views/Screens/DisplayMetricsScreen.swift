//
//  DisplayMetricsScreen.swift
//  meApp
//
//  Created by Lakshmi Priya on 26/06/25.
//

import SwiftUI

struct DisplayMetricsScreen: View {
    @EnvironmentObject var router: Router<SettingsRoute>
    @Environment(\.appTheme) private var theme
    @State private var metrics: [BodyMetricItem] = BodyMetrics.config.keys.map { BodyMetricItem(id: $0, isOn: true) }
    @State private var extraToggles: [ProgressMetricItem] = [
        ProgressMetricItem(id: "goalProgress", label: ScaleModesStrings.goalProgress, isOn: true),
        ProgressMetricItem(id: "dailyAverage", label: ScaleModesStrings.dailyAverage, isOn: true),
        ProgressMetricItem(id: "weeklyAverage", label: ScaleModesStrings.weeklyAverage, isOn: true),
        ProgressMetricItem(id: "monthlyAverage", label: ScaleModesStrings.monthlyAverage, isOn: true),
    ]
    @State private var showWeightOnlyBanner: Bool = false
    @State private var showWeightOnlyInfo: Bool = false
    @State private var showHeartRateBanner: Bool = false
    
    let lang = ScaleModesStrings.self

    var body: some View {
        VStack(alignment: .center, spacing: 0) {
            NavbarHeaderView(
                title: lang.displayMetricsTitle,
                leadingContent: { Image(AppAssets.chevronLeft) },
                trailingContent: {
                    AnyView(ButtonView(
                        text: CommonStrings.save.uppercased(),
                        type: .inlineTextPrimary,
                        size: .small,
                        isDisabled: false,
                        action: {
                            // TODO: ADD Action
                        }
                    ))
                },
                onLeadingTap: { router.navigateBack() },
                onTrailingTap: {},
                canShowBorder: true
            )

            ScrollView (showsIndicators: false){
                VStack(alignment: .leading, spacing: 0) {
                    
                    if showWeightOnlyBanner || showWeightOnlyInfo || showHeartRateBanner {
                        VStack(spacing: .spacingSM){
                            if showWeightOnlyBanner {weightOnlyBanner()}
                            if showWeightOnlyInfo {weightOnlyInfo()}
                            if showHeartRateBanner {heartRateBanner()}
                        }
                        .padding(.top, .spacingMD)
                        .padding(.bottom, .spacingMD)
                    }
                    
                    Text(lang.displayMetricsDescription)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)
                        .padding(.horizontal, .spacingSM)
                        .padding(.top, .spacingMD)
                        .padding(.bottom, .spacingMD)

                    // Metrics List with drag-and-drop reordering
                    List {
                        ForEach($metrics) { $metric in
                            let config = BodyMetrics.config[metric.id]!
                            ToggleListItem(
                                isOn: $metric.isOn,
                                text: config.expandedLabel ?? config.label,
                                icon: config.icon,
                                isDisabled: metric.isDisabled
                            )
                        }
                        .onMove { indices, newOffset in
                            metrics.move(fromOffsets: indices, toOffset: newOffset)
                        }
                    }
                    .listStyle(.plain)
                    .contentMargins(.all, 0, for: .scrollContent)
                    .listStyle(.grouped)
                    .environment(\.editMode, .constant(.active))
                    .frame(height: CGFloat(metrics.count * 56))
                    .background(theme.backgroundPrimary)
                    .cornerRadius(.radiusXS)
                    .scrollIndicators(.hidden)

                    // Extra toggles list with drag-and-drop reordering
                    List {
                        ForEach($extraToggles) { $toggle in
                            ToggleListItem(
                                isOn: $toggle.isOn,
                                text: toggle.label
                            )
                        }
                        .onMove { indices, newOffset in
                            extraToggles.move(fromOffsets: indices, toOffset: newOffset)
                        }
                    }
                    .listStyle(.plain)
                    .contentMargins(.all, 0, for: .scrollContent)
                    .listStyle(.grouped)
                    .environment(\.editMode, .constant(.active))
                    .frame(height: CGFloat(extraToggles.count * 56))
                    .background(theme.backgroundPrimary)
                    .cornerRadius(.radiusXS)
                    .padding(.top, .spacingSM)
                    .scrollIndicators(.hidden)
                }
            }
            .padding(.horizontal, .spacingSM)
            .frame(maxWidth: .infinity, alignment: .top)
        }
        .frame(maxHeight: .infinity, alignment: .top)
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .navigationBarBackButtonHidden(true)
    }

    // MARK: - Helper Private Functions
    private func weightOnlyBanner() -> some View {
        let commonLang = CommonStrings.self
        @State var isWeightOnlyModeOn: Bool = true
        return NoteBox {
            HStack(spacing: .spacingSM) {
                StatusRowView(
                    iconName: AppAssets.weightOnlyMode,
                    label: lang.weightOnlyLabel,
                    statusText: isWeightOnlyModeOn ? commonLang.on.uppercased() : commonLang.off.uppercased()
                )
                .fontWeight(.regular)
                Spacer()
                ButtonView(text: commonLang.update.uppercased(), type: .textPrimary, size: .small, isDisabled: false, action: {})
            }
        }
    }

    private func weightOnlyInfo() -> some View {
        NoteBox {
            VStack(alignment: .leading, spacing: .spacingXS) {
                HStack(){
                    AppIconView(icon: AppAssets.weightOnlyMode, size: IconSize(width: 20, height: 20))
                        .foregroundColor(theme.statusIconPrimary)

                    Text(lang.weightOnlyBannerTitle)
                        .fontOpenSans(.body3)
                        .fontWeight(.bold)
                        .foregroundColor(theme.textHeading)
                }

                Text(lang.weightOnlyBannerDescription)
                    .fontOpenSans(.body3)
                    .foregroundColor(theme.textBody)
            }
        }
    }

    private func heartRateBanner() -> some View {
        let commonLang = CommonStrings.self
        @State var isHeartRateOn: Bool = true
        let iconAndLabelColor = isHeartRateOn ? theme.statusIconPrimary : theme.statusIconSecondary

        return NoteBox {
            HStack(spacing: .spacingSM) {
                StatusRowView(
                    iconName: AppAssets.heartIcon,
                    label: commonLang.heartRateLabel,
                    statusText: isHeartRateOn ? commonLang.on.uppercased() : commonLang.off.uppercased(),
                    foregroundColor: iconAndLabelColor
                )
                Spacer()
                ButtonView(text: commonLang.update.uppercased(), type: .textPrimary, size: .small, isDisabled: false, action: {})
            }
        }
    }
}
