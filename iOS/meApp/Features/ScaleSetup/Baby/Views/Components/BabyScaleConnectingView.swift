//
//  BabyScaleConnectingView.swift
//  meApp
//

import SwiftUI

/// Baby scale connecting step — shows a loading state while pairing is in progress.
/// The failure state is handled by the separate .connectionError step.
struct BabyScaleConnectingView: View {
    @Environment(\.appTheme) private var theme
    private let scaleSetupStrings = ScaleSetupStrings.self

    var body: some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(spacing: .spacingMD) {
                    Text(scaleSetupStrings.connectingToBluetooth)
                        .fontOpenSans(.heading4)
                        .fontWeight(.bold)
                        .foregroundColor(theme.textHeading)
                        .multilineTextAlignment(.leading)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, .spacingXS)
                        .padding(.top, .spacingMD)

                    Spacer()

                    VStack(spacing: .spacingMD) {
                        SetupLoaderView(connectionState: .loading)

                        ConnectionIndicatorView(
                            image: AppAssets.bluetooth,
                            isFailure: false,
                            showPulsingCircle: false
                        )
                    }

                    Spacer()
                }
                .frame(minHeight: geometry.size.height)
                .frame(maxWidth: .infinity)
            }
        }
    }
}
