//
//  ConnectingBluetoothView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 19/07/25.
//

import SwiftUI

struct ConnectingBluetoothView: View {
    @Environment(\.appTheme) private var theme
    var sku: String
    var connectionState: ConnectionState = .loading
    var pairAgain: (() -> Void)?
    private let lang = BluetoothSetupViewStrings.ConnectingBluetoothViewStrings.self
    private let appAssets = AppAssets.self
    
    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(spacing: .spacingLG) {
                VStack(alignment: .leading, spacing: .spacingXS) {
                    Text(lang.title)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)
                        .multilineTextAlignment(.leading)
                        .lineLimit(nil)
                    
                    Text(lang.description)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)
                        .multilineTextAlignment(.leading)
                        .lineLimit(nil)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, .spacingXSM)
                
                VStack(alignment: .center) {
                    GifView(gifName: appAssets.setupPressUnitButtonGifName(sku), height: 250)
                        .frame(width: DevicePlatform.isMiniPhone ? 350 : 370,
                               height: DevicePlatform.isMiniPhone ? 200 : 250)
                        .scaleEffect(DevicePlatform.isMiniPhone ? 0.8 : 0.9)
                        .accessibilityHidden(true)
                }
                .frame(maxWidth: .infinity, alignment: .center)

                // Connection state content
                switch connectionState {
                case .loading:
                    HStack(alignment: .center, spacing: .spacingXS) {
                        Text(lang.pairing)
                            .fontOpenSans(.body1)
                            .foregroundColor(theme.textBody)

                        LoadingDotsView(color: theme.textBody)
                            .offset(y: .spacingXS)
                            .accessibilityHidden(true)
                    }
                    .frame(maxWidth: .infinity, alignment: .center)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(BluetoothSetupViewStrings.A11y.pairingLoadingLabel)
                case .success:
                    Text(lang.paired)
                        .fontOpenSans(.body1)
                        .foregroundColor(theme.textBody)
                        .frame(maxWidth: .infinity, alignment: .center)
                        .accessibilityLabel(BluetoothSetupViewStrings.A11y.pairedLabel)

                case .failure:
                    ButtonView(
                        text: lang.pairAgain,
                        type: .filledPrimary,
                        size: .large,
                        isDisabled: false
                    ) {
                            pairAgain?()
                        }
                    .accessibilityHint(BluetoothSetupViewStrings.A11y.pairAgainHint)
                    .appAccessibility(id: AccessibilityID.bluetoothConnectingPairAgainButton)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.horizontal, .spacingSM)
                default:
                    EmptyView()
                }
            }
            .padding(.top, .spacingLG)
        }
    }
}

#Preview("Loading") {
    ConnectingBluetoothView(
        sku: "0376",
        connectionState: .loading
    )
}

#Preview("Success") {
    ConnectingBluetoothView(
        sku: "0376",
        connectionState: .success
    )
}

#Preview("Failure") {
    ConnectingBluetoothView(
        sku: "0376",
        connectionState: .failure
    ) { }
}
