//  TroubleShootingView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 24/06/25.

import SwiftUI

// MARK: - TroubleShootingView
/// Shows advanced troubleshooting actions and app diagnostics. Triggered from HelpScreen via a hidden gesture. 
/// This view is used to show the debug menu in the help screen.
struct TroubleShootingView: View {
    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var helpStore: HelpStore
    private let lang = HelpScreenStrings.self
    private let commonLang = CommonStrings.self

    // MARK: Dynamic values
    private var appVersion: String { "\(AppInfo.appVersion)" }
    private var currentTime: String { DateTimeTools.getCurrentDayTimeShort() }
    private var timezoneString: String { DateTimeTools.getTimezoneOffsetString() }

    var body: some View {
        VStack(spacing: 0) {
            // Header
            NavbarHeaderView<Image, EmptyView>(
                title: lang.debugMenuTitle,
                leadingContent: { Image(AppAssets.xmark) },
                onLeadingTap: { dismiss() },
                canShowBorder: true,
                canShowPresentationIndicator: true
            )

            ZStack {
                theme.backgroundSecondary.ignoresSafeArea()

                List {
                    cautionSection()
                    appInformationSection()
                    appTroubleshootingSection()
                    scaleTroubleshootingSection()
                }
                .listStyle(.insetGrouped)
                .scrollContentBackground(.hidden)
            }
        }
        .background(theme.backgroundSecondary.ignoresSafeArea())
    }

    // MARK: Sections
    private func cautionSection() -> some View {
        VStack(spacing: .spacingXS) {
            Text(lang.cautionTitle)
                .fontOpenSans(.heading3)
                .foregroundColor(theme.textError)
            Text(lang.cautionSub)
                .fontOpenSans(.body2)
                .foregroundColor(theme.textBody)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .listRowBackground(Color.clear)
        .listRowSeparator(.hidden)
    }

    private func appInformationSection() -> some View {
        Section(header: sectionHeader(title: lang.appInformation)) {
            Group {
                ActionListItemView(config: ActionListItemConfig(title: commonLang.appVersion, value: appVersion, chevronType: .none))
                ActionListItemView(config: ActionListItemConfig(title: lang.nativeModules, value: commonLang.yes, chevronType: .none))
                ActionListItemView(config: ActionListItemConfig(title: lang.componentVersion, value: commonLang.iOS, chevronType: .none))
                ActionListItemView(config: ActionListItemConfig(title: lang.api, value: API.baseURL.slice(from: 7, to: 16), chevronType: .none))
                ActionListItemView(config: ActionListItemConfig(title: lang.time, value: currentTime, chevronType: .none))
                ActionListItemView(config: ActionListItemConfig(title: lang.timezone, value: timezoneString, chevronType: .none))
            }
            .listRowInsets()
        }
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtilityPrimary)
    }

    private func appTroubleshootingSection() -> some View {
        Section(header: sectionHeader(title: lang.appTroubleshooting)) {
            Group {
                ActionListItemView(config: ActionListItemConfig(title: lang.sendWeightGurusLog, onTap: { helpStore.sendWeightGurusLog() }))
                ActionListItemView(config: ActionListItemConfig(title: lang.resyncEntries, onTap: { helpStore.resyncEntries() }))
                ActionListItemView(config: ActionListItemConfig(title: lang.clearAllLocalData, onTap: { helpStore.clearAllLocalData() }))
                ActionListItemView(config: ActionListItemConfig(title: lang.rateApp, onTap: { helpStore.showAppRateModal() }))
            }
            .listRowInsets()

        }
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtilityPrimary)
    }

    private func scaleTroubleshootingSection() -> some View {
        Section(header: sectionHeader(title: lang.scaleTroubleshooting)) {
            ActionListItemView(config: ActionListItemConfig(title: lang.sendScaleLog, chevronType: .right, onTap: { helpStore.sendScaleLog() }))
                .listRowInsets()
        }
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtilityPrimary)
    }

    private func sectionHeader(title: String) -> some View {
        Text(title)
            .fontOpenSans(.heading4)
            .foregroundColor(theme.textHeading)
            .textCase(.none)
            .padding(.bottom, .spacingXS)
            .padding(.leading, -16)
    }
}

// MARK: - Preview
#Preview {
    TroubleShootingView()
        .environmentObject(HelpStore())
        .environmentObject(Theme.shared)
} 
