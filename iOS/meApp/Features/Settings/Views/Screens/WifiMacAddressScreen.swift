//
//  WifiMacAddressScreen.swift
//  meApp
//
//  Created by Lakshmi Priya on 30/06/25.
//

import SwiftUI
import UIKit

// MARK: - ViewModel
@MainActor
final class WifiMacAddressViewModel: ObservableObject {
    @Published var macAddress: String
    @Injector private var notificationService: NotificationHelperService
    
    init(macAddress: String) {
        self.macAddress = macAddress
    }
    
    func copyMacAddress() {
        UIPasteboard.general.string = macAddress
        notificationService.showToast(ToastModel(message: ToastStrings.copiedToClipboard))
    }
}

// MARK: - Screen
struct WifiMacAddressScreen: View {
    @EnvironmentObject var router: Router<SettingsRoute>
    @Environment(\.appTheme) private var theme
    @StateObject private var viewModel: WifiMacAddressViewModel
    let lang = WifiMacAddressScreenStrings.self
    
    init(macAddress: String) {
        _viewModel = StateObject(wrappedValue: WifiMacAddressViewModel(macAddress: macAddress))
    }

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
                        Text(viewModel.macAddress)
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
                                viewModel.copyMacAddress()
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
        
    }
}

#Preview {
    WifiMacAddressScreen(macAddress: "00:11:22:33:44:55")
}
