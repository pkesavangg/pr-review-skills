//
//  AdditionalSettingsSheet.swift
//  meApp
//

import SwiftUI

struct AdditionalSettingsSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.appTheme) private var theme
    @StateObject private var viewModel: AdditionalSettingsViewModel
    private let lang = ScaleSettingsStrings.self
    private let scale: Device

    init(scale: Device) {
        self.scale = scale
        _viewModel = StateObject(wrappedValue: AdditionalSettingsViewModel(scale: scale))
    }

    @State private var showTimeFormatPicker: Bool = false
    @State private var showClearDataPicker: Bool = false

    var body: some View {
        VStack(spacing: 0) {
            NavbarHeaderView(
                title: lang.otherSettings,
                leadingContent: {
                    AppIconView(icon: AppAssets.xmark, size: IconSize(width: 25, height: 22))
                        .foregroundColor(theme.statusIconPrimary)
                },
                trailingContent: {
                    Button {
                        viewModel.showHelpModal()
                    } label: {
                        AppIconView(icon: AppAssets.helpCircle)
                            .foregroundColor(theme.statusIconPrimary)
                    }
                },
                onLeadingTap: { dismiss() },
                onTrailingTap: {},
                canShowPresentationIndicator: true, // Show back button only if not opening from saved scale
            )
            
            List {
                Section(header: SectionHeader(title: lang.scaleFeatures)) {
                    ActionListItemView(
                        config: ActionListItemConfig(
                            title: lang.startAnimation,
                            chevronType: .none,
                            toggleBinding: Binding(get: { viewModel.startAnimationEnabled }, set: { val in
                                viewModel.startAnimationEnabled = val
                            }),
                            isDisabled: !(viewModel.isDeviceConnected),
                            onTap: {
                                Task { await viewModel.setStartAnimation(viewModel.startAnimationEnabled) }
                            }
                        )
                    )
                    
                    ActionListItemView(
                        config: ActionListItemConfig(
                            title: lang.endAnimation,
                            chevronType: .none,
                            toggleBinding: Binding(get: { viewModel.endAnimationEnabled }, set: { val in
                                viewModel.endAnimationEnabled = val
                            }),
                            isDisabled: !(viewModel.isDeviceConnected),
                            onTap: {
                                Task { await viewModel.setEndAnimation(viewModel.endAnimationEnabled) }
                            }
                        )
                    )
                    ActionListItemView(
                        config: ActionListItemConfig(
                            title: lang.clearData,
                            onTap: { showClearDataPicker = true }
                        )
                    )
                    ActionListItemView(
                        config: ActionListItemConfig(
                            title: lang.timeFormat,
                            value: (viewModel.scale.r4ScalePreference?.timeFormat ?? "12") + "H",
                            onTap: { showTimeFormatPicker = true }
                        )
                    )
                    ActionListItemView(
                        config: ActionListItemConfig(
                            title: lang.resetFirmware,
                            onTap: { Task { await viewModel.resetFirmware() } }
                        )
                    )
                    ActionListItemView(
                        config: ActionListItemConfig(
                            title: lang.restoreFactorySettings,
                            onTap: { Task { await viewModel.restoreFactorySettings() } }
                        )
                    )
                }
                .listRowInsets()
                .listRowBackground(theme.backgroundPrimary)
                .listRowSeparatorTint(theme.statusUtilityPrimary)
                
                Section(header: SectionHeader(title: "Scale Details")) {
                    ActionListItemView(config: ActionListItemConfig(title: "Manufacturer", value: viewModel.deviceInfo?.manufacturerName, chevronType: .none))
                    ActionListItemView(config: ActionListItemConfig(title: "Model Number", value: viewModel.deviceInfo?.modelNumber, chevronType: .none))
                    ActionListItemView(config: ActionListItemConfig(title: "Serial Number", value: viewModel.deviceInfo?.serialNumber, chevronType: .none))
                    ActionListItemView(config: ActionListItemConfig(title: "Hardware Revision", value: viewModel.deviceInfo?.hardwareRevision, chevronType: .none))
                    ActionListItemView(config: ActionListItemConfig(title: "Firmware Revision", value: viewModel.deviceInfo?.firmwareRevision, chevronType: .none))
                    if let battery = viewModel.deviceInfo?.batteryLevel { ActionListItemView(config: ActionListItemConfig(title: "Battery Level", value: "\(battery)%", chevronType: .none)) }
                }
                .listRowInsets()
                .listRowBackground(theme.backgroundPrimary)
                .listRowSeparatorTint(theme.statusUtilityPrimary)
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .navigationTitle(lang.otherSettings)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(action: { dismiss() }) { Image(AppAssets.chevronLeft) }
                }
            }
            .background(theme.backgroundSecondary.ignoresSafeArea())
            .onAppear { Task { await viewModel.load() } }
            .confirmationDialog(
                lang.selectTimeFormat,
                isPresented: $showTimeFormatPicker,
                titleVisibility: .visible
            ) {
                Button("12H") {
                    Task { await viewModel.setTimeFormat("12H") }
                }
                Button("24H") {
                    Task { await viewModel.setTimeFormat("24H") }
                }
                Button("Cancel", role: .cancel) {}
            }
            .confirmationDialog(
                lang.clearData,
                isPresented: $showClearDataPicker,
                titleVisibility: .visible
            ) {
                Button("All") { Task { await viewModel.clearData(.all) } }
                Button("Wi‑Fi") { Task { await viewModel.clearData(.wifi) } }
                Button("Settings") { Task { await viewModel.clearData(.settings) } }
                Button("History") { Task { await viewModel.clearData(.history) } }
                Button("Account") { Task { await viewModel.clearData(.userData) } }
                Button("Cancel", role: .cancel) {}
            }
        }
    }
}
