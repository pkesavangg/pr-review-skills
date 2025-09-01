//
//  SoftwareUpdateSheet.swift
//  meApp
//

import SwiftUI

struct SoftwareUpdateSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.appTheme) private var theme
    @StateObject private var viewModel: SoftwareUpdateViewModel
    
    init(scale: Device, currentFirmware: String?, latestVersion: String?) {
        _viewModel = StateObject(wrappedValue: SoftwareUpdateViewModel(scale: scale, currentFirmware: currentFirmware, latestVersion: latestVersion))
    }
    
    @State private var segment: Segment = .now
    @State private var showDatePicker: Bool = false
    @State private var showTimePicker: Bool = false
    
    enum Segment: String, CaseIterable, Identifiable, Hashable {
        case now = "Now"
        case scheduled = "Schedule"
        var id: String { rawValue }
    }
    
    var body: some View {
        VStack(spacing: 0) {
            NavbarHeaderView(
                title: FirmwareUpdateStrings.title,
                leadingContent: { AppIconView(icon: AppAssets.xmark) },
                trailingContent: { EmptyView() },
                onLeadingTap: { dismiss() },
                onTrailingTap: {},
                canShowPresentationIndicator: true
            )
            VStack {
                ScrollView(.vertical, showsIndicators: false) {
                    if !viewModel.hasUpdate {
                        // Update available flow (matches Ionic text)
                        VStack(alignment: .leading, spacing: .spacingLG) {
                            Text("\(FirmwareUpdateStrings.message) \(viewModel.latestVersion ?? "") \(FirmwareUpdateStrings.message1)")
                                .fontOpenSans(.body2)
                                .foregroundColor(theme.textBody)
                            
                            // Segmented control using shared SegmentedButtonView
                            SegmentedButtonView(segments: Segment.allCases, selectedSegment: $segment)
                            
                            if segment == .now {
                                VStack(alignment: .leading, spacing: .spacingLG) {
                                    Text(FirmwareUpdateStrings.nowDetails)
                                        .fontOpenSans(.body2)
                                        .foregroundColor(theme.textBody)
                                    
                                    // Save button
                                    ButtonView(
                                        text: FirmwareUpdateStrings.upgradeNow,
                                        type: .filledPrimary,
                                        size: .large,
                                        isDisabled: viewModel.isUpdating,
                                    ) {
                                        Task {
                                            await viewModel.updateSoftware(isScheduled: false)
                                        }
                                    }
                                    .frame(maxWidth: .infinity, alignment: .center)
                                }
                            } else {
                                VStack(alignment: .leading, spacing: .spacingSM) {
                                    Text(FirmwareUpdateStrings.date)
                                        .fontOpenSans(.heading4)
                                        .foregroundColor(theme.textHeading)
                                    HStack(spacing: .spacingSM) {
                                        DateLabelView(date: viewModel.selectedDate) {
                                            withAnimation {
                                                showDatePicker.toggle()
                                                if showTimePicker { showTimePicker = false }
                                            }
                                        }
                                        TimeLabelView(time: viewModel.selectedTime) {
                                            withAnimation {
                                                showTimePicker.toggle()
                                                if showDatePicker { showDatePicker = false }
                                            }
                                        }
                                    }
                                    Divider()
                                    DatePickerView(
                                        isPresented: $showDatePicker,
                                        date: $viewModel.selectedDate,
                                        endDate: Date()
                                    )
                                    TimePickerView(
                                        isPresented: $showTimePicker,
                                        time: $viewModel.selectedTime,
                                        endTime: Date()
                                    )
                                    
                                    // Save button
                                    ButtonView(
                                        text: CommonStrings.save,
                                        type: .filledPrimary,
                                        size: .large,
                                        isDisabled: viewModel.isUpdating,
                                    ) {
                                        Task {
                                            await viewModel.updateSoftware(isScheduled: true)
                                        }
                                    }
                                    .frame(maxWidth: .infinity, alignment: .center)
                                }
                            }
                        }
                        .padding(.top, .spacingLG)
                    } else {
                        // Segmented control using shared SegmentedButtonView
                        VStack(spacing: .spacingSM) {
                            Text("\(FirmwareUpdateStrings.version) \(viewModel.latestVersion ?? viewModel.currentFirmware ?? "00:00:00:00:00:00")")
                                .fontOpenSans(.body2)
                                .foregroundColor(theme.textBody)
                            Text(FirmwareUpdateStrings.alreadyUpdated)
                                .fontOpenSans(.body2)
                                .foregroundColor(theme.textBody)
                            AppIconView(icon: AppAssets.checkMarkLarge, size: IconSize(width: 180, height: 180))
                                .foregroundColor(theme.statusIconPrimary)
                                .frame(maxWidth: .infinity, alignment: .center)
                        }
                        .padding(.top, .spacingLG)
                    }
                }
            }
            .padding(.horizontal, .spacingSM)
            
            
        }
        .background(theme.backgroundSecondary.ignoresSafeArea())
    }
}


