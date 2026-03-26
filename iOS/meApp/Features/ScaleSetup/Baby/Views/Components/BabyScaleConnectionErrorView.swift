//
//  BabyScaleConnectionErrorView.swift
//  meApp
//

import SwiftUI

/// Baby scale connection screen — shows a loading state while pairing,
/// then switches to the error layout (left-aligned heading + message + buttons) on failure.
struct BabyScaleConnectionErrorView: View {
    @EnvironmentObject var store: BabyScaleSetupStore
    var onPairAgain: () -> Void = {}
    var onSupport: () -> Void = {}

    @Environment(\.appTheme) private var theme
    private let lang = BabyScaleSetupStrings.Connection.self
    private let scaleSetupStrings = ScaleSetupStrings.self

    var body: some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(spacing: .spacingMD) {
                    if store.connectionState == .failure {
                        failureContent
                    } else {
                        loadingContent
                    }
                }
                .frame(minHeight: geometry.size.height)
                .frame(maxWidth: .infinity)
            }
        }
    }

    // MARK: - Failure

    private var failureContent: some View {
        VStack(spacing: .spacingMD) {
            VStack(alignment: .leading, spacing: .spacingXS) {
                Text(lang.unableToConnect)
                    .fontOpenSans(.heading4)
                    .fontWeight(.bold)
                    .foregroundColor(theme.textHeading)
                    .multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)

                Text(lang.interferenceMessage)
                    .fontOpenSans(.body2)
                    .foregroundColor(theme.textBody)
                    .multilineTextAlignment(.leading)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, .spacingXS)
            .padding(.top, .spacingMD)

            Spacer()

            VStack(spacing: .spacingMD) {
                ButtonView(
                    text: lang.pairAgain,
                    type: .filledPrimary,
                    size: .large,
                    isDisabled: false,
                    action: onPairAgain
                )

                ButtonView(
                    text: lang.support,
                    type: .inlineTextPrimary,
                    size: .large,
                    isDisabled: false,
                    action: onSupport
                )
            }
            .padding(.bottom, .spacingLG)
        }
    }

    // MARK: - Loading

    private var loadingContent: some View {
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
                SetupLoaderView(connectionState: store.connectionState)
                    .id(store.connectionState)

                ConnectionIndicatorView(
                    image: AppAssets.bluetooth,
                    isFailure: false,
                    showPulsingCircle: false
                )
            }

            Spacer()
        }
    }
}
