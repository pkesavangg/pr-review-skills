//  HelpScreen.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 24/06/25.

import SwiftUI

// MARK: - Help Screen
struct HelpScreen: View {
    @Environment(\.appTheme) private var theme
    @StateObject var helpStore = HelpStore()
    @EnvironmentObject var router: Router<SettingsRoute>

    @State private var expandedCategory: DeviceCategory?

    private let lang = HelpScreenStrings.self
    private let commonLang = CommonStrings.self
    private var appVersion: String { "\(AppInfo.appVersion)" }
    private var fallbackProductURL: URL { AppConstants.LegalURLs.greaterGoodsWebsite }

    // MARK: - Device Categories
    enum DeviceCategory: CaseIterable {
        case babyScale
        case bloodPressureMonitor
        case weightScale

        var title: String {
            switch self {
            case .weightScale: return HelpScreenStrings.weightScale
            case .babyScale: return HelpScreenStrings.babyScale
            case .bloodPressureMonitor: return HelpScreenStrings.bloodPressureMonitor
            }
        }

        var icon: String {
            switch self {
            case .weightScale: return AppAssets.weightScaleIcon
            case .babyScale: return AppAssets.babyAppIcon
            case .bloodPressureMonitor: return AppAssets.bpmIcon
            }
        }

        var scales: [DeviceItemInfo] {
            switch self {
            case .weightScale:
                return SCALES.filter { $0.setupType != .babyScale }
            case .babyScale:
                return SCALES.filter { $0.setupType == .babyScale }
            case .bloodPressureMonitor:
                return BPMS
            }
        }
    }
    var body: some View {
        VStack(spacing: 0) {
            // Header
            NavbarHeaderView<AppIconView, EmptyView>(
                title: lang.title,
                leadingContent: { AppIconView(icon: AppAssets.chevronLeft) },
                onLeadingTap: { router.navigateBack() },
                onTitleTap: {
                    helpStore.handleHeaderTap()
                },
                canShowBorder: true
            )
            .contentShape(Rectangle())
            .onTapGesture {
                helpStore.handleHeaderTap()
            }
            
            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: .spacingLG) {
                    talkToTeamSection()
                        .padding(.horizontal, .spacingSM)
                    digitalManualSection()
                    Text("\(commonLang.appVersion) \(appVersion)")
                        .foregroundColor(theme.textBody)
                        .frame(maxWidth: .infinity, alignment: .center)
                        .padding(.bottom, .spacingSM)
                }
                .padding(.top, .spacingLG)
            }
        }
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .navigationBarHidden(true)
        .inAppBrowser(
            url: helpStore.productURL ?? fallbackProductURL,
            isPresented: $helpStore.showProductBrowser
        )
        // Debug menu sheet uses store's flag
        .sheet(isPresented: $helpStore.showDebugMenu,
               onDismiss: { helpStore.dismissDebugMenu() },
               content: {
            TroubleShootingView()
                .environmentObject(helpStore)
        })
    }
    
    // MARK: Sections
    private func talkToTeamSection() -> some View {
        VStack(alignment: .leading, spacing: .spacingXS) {
            Text(lang.talkToOurTeamTitle)
                .fontOpenSans(.heading4)
                .foregroundColor(theme.textHeading)
            Text(lang.talkToOurTeamSub)
                .fontOpenSans(.body2)
                .foregroundColor(theme.textBody)
            
            VStack(alignment: .leading, spacing: .spacingMD) {
                CallButtonView()
                EmailButtonView()
            }
            .padding(.top, .spacingXS)
        }
    }
    
    private func digitalManualSection() -> some View {
        VStack(alignment: .leading, spacing: .spacingXS) {
            Group {
                HStack {
                    Text(lang.digitalManualTitle)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)

                    Spacer()
                    Button(action: {
                        helpStore.openHelp()
                    }, label: {
                        AppIconView(icon: AppAssets.helpCircle)
                            .foregroundColor(theme.actionPrimary)
                    })
                }
                Text(lang.digitalManualSub)
                    .fontOpenSans(.body2)
                    .foregroundColor(theme.textBody)
            }
            .padding(.horizontal, .spacingSM)

            // Device category cards
            VStack(spacing: 0) {
                ForEach(Array(DeviceCategory.allCases.enumerated()), id: \.element.title) { index, category in
                    deviceCategoryCard(category, isFirst: index == 0)
                }
            }
            .padding(.top, .spacingSM)
        }
    }

    // MARK: - Device Category Card
    // swiftlint:disable:next function_body_length
    private func deviceCategoryCard(_ category: DeviceCategory, isFirst: Bool = false) -> some View {
        let isExpanded = expandedCategory == category
        return VStack(spacing: 0) {
            // Header row
            HStack(spacing: .spacingXS) {
                Image(category.icon)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 24, height: 24)

                Text(category.title)
                    .fontOpenSans(.heading5)
                    .foregroundColor(theme.textBody)

                Spacer()

                AppIconView(icon: AppAssets.chevronDown)
                    .foregroundColor(theme.statusIconPrimary)
                    .rotationEffect(.degrees(isExpanded ? 180 : 0))
            }
            .padding(.spacingSM)
            .frame(height: 88)
            .contentShape(Rectangle())
            .onTapGesture {
                withAnimation(.easeInOut(duration: 0.25)) {
                    expandedCategory = isExpanded ? nil : category
                }
            }
            .border(sides: isFirst ? [.top] : [], thickness: 0.5, color: theme.statusUtilityPrimary)
            .border(sides: [.bottom], thickness: isExpanded ? 0 : 0.5, color: theme.statusUtilityPrimary)

            // Expanded content
            if isExpanded {
                if category == .weightScale {
                    // Weight scales use the existing segmented filter list
                    DeviceManualListView(scales: category.scales) { scale in
                        helpStore.openProductManual(sku: scale.sku)
                    }
                    .padding(.top, .spacingSM)
                    .transition(.opacity)
                } else {
                    LazyVStack(spacing: 0) {
                        let scales = category.scales
                        ForEach(Array(scales.enumerated()), id: \.element.id) { index, scale in
                            Button {
                                helpStore.openProductManual(sku: scale.sku)
                            } label: {
                                DeviceManualListRowView(
                                    scale: scale,
                                    showConnectivityIcon: false,
                                    showBottomBorder: index < scales.count - 1
                                )
                            }
                        }
                    }
                    .background(theme.backgroundPrimary)
                    .clipShape(RoundedRectangle(cornerRadius: .spacingSM))
                    .padding(.horizontal, .spacingSM)
                    .padding(.vertical, .spacingXS)
                    .transition(.opacity)
                }
            }
        }
        .animation(.easeInOut(duration: 0.25), value: isExpanded)
    }
}

// MARK: - Preview
#Preview {
    HelpScreen()
        .environmentObject(HelpStore())
        .environmentObject(Router<SettingsRoute>())
}
