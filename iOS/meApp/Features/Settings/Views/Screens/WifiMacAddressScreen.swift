//
//  WifiMacAddressScreen.swift
//  meApp
//
//  Created by Lakshmi Priya on 30/06/25.
//

import SwiftUI

struct WifiMacAddressScreen: View {
    @EnvironmentObject var router: Router<SettingsRoute>
    @Environment(\.appTheme) private var theme
    @ObservedObject var scaleStore = ScaleStore()
    let scale: Device
    let lang = WifiMacAddressScreenStrings.self

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            NavbarHeaderView(
                title: lang.title,
                leadingContent: { Image(AppAssets.chevronLeft) },
                trailingContent: { EmptyView() },
                onLeadingTap: { router.navigateBack() },
                onTrailingTap: {},
                canShowBorder: true
            )

            VStack(alignment: .leading, spacing: .spacingMD) {
                Text(lang.subtitle)
                    .fontOpenSans(.heading4)
                    .fontWeight(.bold)
                    .foregroundColor(theme.textHeading)

                VStack(alignment: .leading, spacing: .spacingXS) {
                    NoteBox(alignCenter: true){
                        Text(scaleStore.getWifiMacAddressString())
                            .fontOpenSans(.body2)
                            .foregroundColor(theme.textBody)
                    }
                    .frame(maxWidth: .infinity)

                    HStack {
                        ButtonView(
                            text: lang.copyButton,
                            type: .textPrimary,
                            size: .large,
                            isDisabled: false,
                            action: {
                                UIPasteboard.general.string = scaleStore.getWifiMacAddressString()
                            }
                        )
                    }
                    .frame(maxWidth: .infinity, alignment: .center)
                }

                Text(lang.instruction)
                    .fontOpenSans(.body2)
                    .foregroundColor(theme.textBody)
            }
            .padding(.top, .spacingLG)
            .padding(.horizontal, .spacingSM)
        }
        .frame(maxHeight: .infinity, alignment: .top)
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .navigationBarBackButtonHidden(true)
        .onAppear {
            Task {
                await scaleStore.loadScale(scale)
                // Only fetch WiFi MAC if it's a connected R4 scale
                if scaleStore.shouldFetchWifiMacAddress(for: scale) {
                    await scaleStore.fetchWifiMacAddress()
                }
            }
        }
    }
}

#Preview {
    let mockDevice = Device(
        id: "1",
        accountId: "demo-account",
        sku: "0412",
        deviceName: "AccuCheck Verve Smart Scale"
    )
    WifiMacAddressScreen(scale: mockDevice)
}
