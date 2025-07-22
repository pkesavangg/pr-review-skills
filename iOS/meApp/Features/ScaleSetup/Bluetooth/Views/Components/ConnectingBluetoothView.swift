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
                
                GifView(gifName: appAssets.setupPressUnitButtonGifName(sku))
                    .frame(height: 211)
                    .frame(maxWidth: .infinity)
                
                // Connection state content
                switch connectionState {
                case .loading:
                    VStack(spacing: .spacingMD) {
                        HStack(alignment: .center, spacing: .spacingXS) {
                            Text(lang.pairing)
                                .fontOpenSans(.body1)
                                .foregroundColor(theme.textBody)
                            
                            LoadingDotsView(color: theme.textBody)
                                .offset(y: .spacingXS)
                        }
                    }
                case .success:
                    VStack(spacing: .spacingMD) {
                        Text(lang.paired)
                            .fontOpenSans(.body1)
                            .foregroundColor(theme.textBody)
                    }
                    
                case .failure:
                    VStack(spacing: .spacingMD) {
                        ButtonView(
                            text: lang.pairAgain,
                            type: .filledPrimary,
                            size: .large,
                            isDisabled: false,
                            action: {
                                pairAgain?()
                            }
                        )
                    }
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
        connectionState: .failure,
        pairAgain: {
            print("Pair again tapped")
        }
    )
}
