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
    @Environment(\.registerTabDeactivationHandler) private var registerDeactivation
    @StateObject private var viewModel: DisplayMetricsViewModel
    let lang = ScaleModesStrings.self
    
    let scale: Device
    let isWeighOnlyModeEnabledByOthers: Bool
    
    init(scale: Device, isWeighOnlyModeEnabledByOthers: Bool = false) {
        self.scale = scale
        self.isWeighOnlyModeEnabledByOthers = isWeighOnlyModeEnabledByOthers
        _viewModel = StateObject(wrappedValue: DisplayMetricsViewModel(scale: scale, isWeighOnlyModeEnabledByOthers: isWeighOnlyModeEnabledByOthers))
    }
    
    var body: some View {
        VStack(alignment: .center, spacing: 0) {
            NavbarHeaderView(
                title: DisplayMetricStrings.displayMetricsTitle,
                leadingContent: { AppIconView(icon: AppAssets.chevronLeft) },
                trailingContent: {
                    ButtonView(
                        text: CommonStrings.save.uppercased(),
                        type: .inlineTextPrimary,
                        size: .small,
                        isDisabled: !viewModel.hasChanges
                    ) {
                            Task {
                                await viewModel.saveDisplayMetrics()
                                router.navigateBack()
                            }
                        }
                },
                onLeadingTap: { router.navigateBack() },
                canShowBorder: true
            )
            
            List {
                bannerSection()
                descriptionSection()
                
                // Body Metrics Section
                MetricsSectionView(
                    metrics: Binding(
                        get: { viewModel.metrics },
                        set: { viewModel.updateMetrics($0) }
                    ),
                    onValueChanged: { viewModel.updateDisplayMetricsValue() },
                    onMove: { indices, newOffset in
                        var updatedMetrics = viewModel.metrics
                        updatedMetrics.move(fromOffsets: indices, toOffset: newOffset)
                        viewModel.updateMetrics(updatedMetrics)
                        viewModel.updateDisplayMetricsValue()
                    },
                    showIcon: true,
                    onToggle: { item, isOn in
                        viewModel.handleBodyMetricToggle(key: item.key, isEnabled: isOn)
                    },
                    shouldDisableToggle: { metric in
                        // Disable only when heart rate is currently off and banner indicates it cannot be enabled
                        return metric.key == "heartRate" && !metric.isEnabled && viewModel.showHeartRateBanner
                    }
                )
                
                // Progress Metrics Section
                MetricsSectionView(
                    metrics: Binding(
                        get: { viewModel.progressMetrics },
                        set: { viewModel.updateProgressMetrics($0) }
                    ),
                    onValueChanged: { viewModel.updateDisplayMetricsValue() },
                    onMove: { indices, newOffset in
                        var updatedMetrics = viewModel.progressMetrics
                        updatedMetrics.move(fromOffsets: indices, toOffset: newOffset)
                        viewModel.updateProgressMetrics(updatedMetrics)
                        viewModel.updateDisplayMetricsValue()
                    },
                    showIcon: false,
                    onToggle: { item, isOn in
                        viewModel.handleProgressMetricToggle(key: item.key, isEnabled: isOn)
                    },
                    shouldDisableToggle: { metric in
                        return metric.key == "heartRate" && !metric.isEnabled && viewModel.showHeartRateBanner
                    }
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
        .task {
            await viewModel.loadDisplayMetricsData()
        }
        .onAppear {
            registerDeactivation {
                await viewModel.allowExit()
            }
        }
        .onDisappear {
            registerDeactivation { true }
        }
    }
    
    // MARK: - Sections as Functions
    private func bannerSection() -> some View {
        Section {
            if viewModel.showWeightOnlyBanner || viewModel.showWeightOnlyInfo || viewModel.showHeartRateBanner {
                VStack(spacing: .spacingSM) {
                    if viewModel.showWeightOnlyBanner { weightOnlyBanner() }
                    if viewModel.showWeightOnlyInfo { weightOnlyInfo() }
                    if viewModel.showHeartRateBanner { heartRateBanner() }
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
                    statusText: viewModel.isWeightOnlyModeOn ? commonLang.on.uppercased() : commonLang.off.uppercased()
                )
                .fontWeight(.regular)
                Spacer()
                ButtonView(text: commonLang.update.uppercased(), type: .textPrimary, size: .small, isDisabled: false) {
                    router.navigate(to: .scaleModes(scale: scale, isWeighOnlyModeEnabledByOthers: isWeighOnlyModeEnabledByOthers))
                }
                .padding(.leading, 5)
            }
        }
    }
    
    private func weightOnlyInfo() -> some View {
        NoteBox {
            VStack(alignment: .leading, spacing: .spacingXS) {
                HStack {
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
        HeartRateBanner(
            isHeartRateOn: viewModel.isHeartRateOn
        ) {
                router.navigate(to: .scaleModes(scale: scale, isWeighOnlyModeEnabledByOthers: isWeighOnlyModeEnabledByOthers))
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
