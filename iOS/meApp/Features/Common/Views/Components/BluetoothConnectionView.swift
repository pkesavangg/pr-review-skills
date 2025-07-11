//
//  BluetoothConnectionView.swift
//  meApp
//
//  Created by Kesavan on 10/07/25.
//

import SwiftUI

/// A reusable view that visualizes the Bluetooth connection lifecycle (loading, success, failure).
///
/// Usage:
/// ```swift
/// BluetoothConnectionView(state: .loading)
/// BluetoothConnectionView(state: .success)
/// BluetoothConnectionView(state: .failure, errorCode: "E42")
/// ```
/// Pass the current `ConnectionState`; you may optionally provide `errorCode` for the failure case.
struct BluetoothConnectionView: View {
    // MARK: - Props
    let state: ConnectionState
    /// Optional error code to display when `state == .failure`.
    var errorCode: String? = nil

    /// Called when the user taps the *Try Again* button (shown only on `.failure`).
    var onTryAgain: () -> Void = {}

    /// Called when the user taps the *Support* button (shown only on `.failure`).
    var onSupport: () -> Void = {}

    @Environment(\.appTheme) private var theme
    private let scaleSetupStrings = ScaleSetupStrings.self
    private let commonStrings = CommonStrings.self

    // MARK: - Computed Helpers
    private var heading: String {
        switch state {
        case .loading: return scaleSetupStrings.connectingToBluetooth
        case .success: return scaleSetupStrings.connectingToBluetooth
        case .failure: return scaleSetupStrings.connectionError
        }
    }

    private var showErrorCode: Bool {
        state == .failure && errorCode != nil && !errorCode!.isEmpty
    }

    // MARK: - Body
    var body: some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(spacing: .spacingMD) {
                    // Heading & error code
                    VStack(spacing: .spacingXS) {
                        Text(heading)
                            .fontOpenSans(.heading4)
                            .fontWeight(.bold)
                            .foregroundColor(theme.textHeading)
                            .multilineTextAlignment(.center)

                        if showErrorCode {
                            Text(scaleSetupStrings.errorCode(errorCode ?? ""))
                                .fontOpenSans(.body2)
                                .foregroundColor(theme.textBody)
                        }
                    }
                    .padding(.horizontal, .spacingLG)

                    VStack(spacing: .spacingMD) {
                        // Loader dots between scale and indicator
                        Image(AppAssets.scale0383)
                            .resizable()
                            .scaledToFit()
                            .frame(width: 180, height: 180)
                            .themeDropShadow()

                        VStack {
                            // Re-instantiate the loader every time the state changes so
                            // we don’t keep the previous animation colours (e.g. red ➜ blue).
                            SetupLoaderView(connectionState: state)
                                .id(state)  // Force a fresh view when the enum value flips

                            ConnectionIndicatorView(
                                image: AppAssets.bluetooth,
                                isFailure: state == .failure,
                                showPulsingCircle: state == .loading
                            )
                        }
                    }

                    // Action buttons (visible only on failure)
                    if state == .failure {
                        VStack(spacing: .spacingMD) {
                            ButtonView(
                                text: commonStrings.tryAgain,
                                type: .filledPrimary,
                                size: .large,
                                isDisabled: false,
                                action: onTryAgain
                            )

                            ButtonView(
                                text:  commonStrings.support,
                                type: .inlineTextPrimary,
                                size: .large,
                                isDisabled: false,
                                action: onSupport
                            )
                        }
                        .padding(.top, .spacingXL)
                    }
                }
                .frame(minHeight: geometry.size.height)
                .frame(maxWidth: .infinity, alignment: .center)
            }}
    }
}

// MARK: - Previews
#Preview("Loading") {
    BluetoothConnectionView(state: .loading)
        .environmentObject(Theme.shared)
}

#Preview("Success") {
    BluetoothConnectionView(state: .success)
        .environmentObject(Theme.shared)
}

#Preview("Failure w/Code") {
    BluetoothConnectionView(state: .failure, errorCode: "E42")
        .environmentObject(Theme.shared)
} 
