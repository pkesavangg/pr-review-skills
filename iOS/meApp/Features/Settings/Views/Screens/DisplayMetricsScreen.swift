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
    @StateObject private var scaleStore = ScaleStore()
    @State private var isWeightOnlyModeOn: Bool = true
    @State private var isHeartRateOn: Bool = true
    let lang = ScaleModesStrings.self

    var body: some View {
        VStack(alignment: .center, spacing: 0) {
            NavbarHeaderView(
                title: DisplayMetricStrings.displayMetricsTitle,
                leadingContent: { Image(AppAssets.chevronLeft) },
                trailingContent: {
                    ButtonView(
                        text: CommonStrings.save.uppercased(),
                        type: .inlineTextPrimary,
                        size: .small,
                        isDisabled: false,
                        action: {
                            scaleStore.saveDisplayMetrics()
                        }
                    )
                },
                onLeadingTap: { router.navigateBack() },
                onTrailingTap: {
                    // TODO: ADD Action
                },
                canShowBorder: true
            )

            List {
                bannerSection()
                descriptionSection()
                
                Section {
                    ForEach($scaleStore.metrics) { $metric in
                        ToggleListItem(
                            isOn: $metric.isEnabled,
                            text: metric.name,
                            icon: metric.imagePath,
                            isDisabled: !metric.isEnabled
                        )
                        .listRowBackground(theme.backgroundPrimary)
                        .listRowInsets(EdgeInsets())
                    }
                    .onMove { indices, newOffset in
                        scaleStore.metrics.move(fromOffsets: indices, toOffset: newOffset)
                    }
                }
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets())

                Section {
                    ForEach($scaleStore.progressMetrics) { $toggle in
                        ToggleListItem(
                            isOn: $toggle.isEnabled,
                            text: toggle.name
                        )
                        .listRowBackground(theme.backgroundPrimary)
                        .listRowInsets(EdgeInsets())
                    }
                    .onMove { indices, newOffset in
                        scaleStore.progressMetrics.move(fromOffsets: indices, toOffset: newOffset)
                    }
                }
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets())

            }
            .scrollContentBackground(.hidden)
            .listStyle(.insetGrouped)
            .scrollIndicators(.hidden)
            .environment(\.editMode, .constant(.active))
        }
        .frame(maxHeight: .infinity, alignment: .top)
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .navigationBarBackButtonHidden(true)
    }
    
    // MARK: - Sections as Functions
    private func bannerSection() -> some View {
        Section {
            if scaleStore.showWeightOnlyBanner || scaleStore.showWeightOnlyInfo || scaleStore.showHeartRateBanner {
                VStack(spacing: .spacingSM) {
                    if scaleStore.showWeightOnlyBanner { weightOnlyBanner() }
                    if scaleStore.showWeightOnlyInfo { weightOnlyInfo() }
                    if scaleStore.showHeartRateBanner { heartRateBanner() }
                }
            }
        }
        .listRowBackground(Color.clear)
        .listRowInsets(EdgeInsets())
    }
    
    private func descriptionSection() -> some View {
        Section {
            Text(lang.displayMetricsDescription)
                .fontOpenSans(.body2)
                .foregroundColor(theme.textBody)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .listRowBackground(Color.clear)
        .listRowInsets(EdgeInsets())
    }
    
    private func weightOnlyBanner() -> some View {
        let commonLang = CommonStrings.self
        return NoteBox {
            HStack(spacing: .spacingSM) {
                StatusRowView(
                    iconName: AppAssets.weightOnlyMode,
                    label: lang.weightOnlyLabel,
                    statusText: isWeightOnlyModeOn ? commonLang.on.uppercased() : commonLang.off.uppercased()
                )
                .fontWeight(.regular)
                Spacer()
                ButtonView(text: commonLang.update.uppercased(), type: .textPrimary, size: .small, isDisabled: false, action: {scaleStore.updateWeightOnlyMode()})
            }
        }
    }

    private func weightOnlyInfo() -> some View {
        NoteBox {
            VStack(alignment: .leading, spacing: .spacingXS) {
                HStack() {
                    AppIconView(icon: AppAssets.weightOnlyMode, size: IconSize(width: 20, height: 20))
                        .foregroundColor(theme.statusIconPrimary)

                    Text(lang.weightOnlyBannerTitle)
                        .fontWeight(.bold)
                        .fontOpenSans(.body3)
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
                ButtonView(text: commonLang.update.uppercased(), type: .textPrimary, size: .small, isDisabled: false, action: {scaleStore.updateHeartRate()})
            }
        }
    }
}
