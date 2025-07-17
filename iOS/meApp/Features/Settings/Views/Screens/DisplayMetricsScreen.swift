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
    
    let scale: Device
    
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
                            Task {
                                await scaleStore.saveDisplayMetrics()
                            }
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
                
                // Body Metrics Section
                MetricsSectionView(
                    metrics: Binding(
                        get: { scaleStore.metrics },
                        set: { scaleStore.updateMetrics($0) }
                    ),
                    onValueChanged: { scaleStore.updateDisplayMetricsValue() },
                    onMove: { indices, newOffset in
                        var updatedMetrics = scaleStore.metrics
                        updatedMetrics.move(fromOffsets: indices, toOffset: newOffset)
                        scaleStore.updateMetrics(updatedMetrics)
                        scaleStore.updateDisplayMetricsValue()
                    },
                    showIcon: true
                )
                
                // Progress Metrics Section
                MetricsSectionView(
                    metrics: Binding(
                        get: { scaleStore.progressMetrics },
                        set: { scaleStore.updateProgressMetrics($0) }
                    ),
                    onValueChanged: { scaleStore.updateDisplayMetricsValue() },
                    onMove: { indices, newOffset in
                        var updatedMetrics = scaleStore.progressMetrics
                        updatedMetrics.move(fromOffsets: indices, toOffset: newOffset)
                        scaleStore.updateProgressMetrics(updatedMetrics)
                        scaleStore.updateDisplayMetricsValue()
                    },
                    showIcon: false
                )
            }
            .scrollContentBackground(.hidden)
            .listStyle(.insetGrouped)
            .scrollIndicators(.hidden)
            .environment(\.editMode, .constant(.active))
        }
        .frame(maxHeight: .infinity, alignment: .top)
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .navigationBarBackButtonHidden(true)
        .onAppear {
            Task {
                await scaleStore.loadScale(scale)
                scaleStore.loadDisplayMetrics()
            }
        }
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
    
    // MARK: - Banner Components
    
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
                ButtonView(text: commonLang.update.uppercased(), type: .textPrimary, size: .small, isDisabled: false, action: {
                    scaleStore.handleWeightOnlyBannerAction()
                    
                })
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
                ButtonView(text: commonLang.update.uppercased(), type: .textPrimary, size: .small, isDisabled: false, action: {
                    scaleStore.updateHeartRateEnabled(!scaleStore.isHeartRateEnabled)
                })
            }
        }
    }
}

#Preview {
    DisplayMetricsScreen(scale: Device(
        id: "preview-scale-id",
        accountId: "preview-account",
        sku: "0412",
        deviceName: "Preview Scale",
        deviceType: "scale"
    ))
    .environmentObject(Theme.shared)
    .environmentObject(Router<SettingsRoute>())
}
