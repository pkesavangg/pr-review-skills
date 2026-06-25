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
/// BluetoothConnectionView(state: .loading, setupType: .btWifiR4)
/// BluetoothConnectionView(state: .success, setupType: .btWifiR4)
/// BluetoothConnectionView(state: .failure, setupType: .btWifiR4, errorCode: "E42")
/// ```
/// Pass the current `ConnectionState`; you may optionally provide `errorCode` for the failure case.
struct BluetoothConnectionView: View {
    // MARK: - Props
    let state: ConnectionState
    let setupType: DeviceSetupType
    /// Optional error code to display when `state == .failure`.
    var errorCode: String?
    
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
        case .success: return scaleSetupStrings.connectedToBluetooth
        case .failure: return scaleSetupStrings.connectionError
        case .noNetworks: return scaleSetupStrings.noNetworksFound
        }
    }
    
    private var showErrorCode: Bool {
        state == .failure && !(errorCode?.isEmpty ?? true)
    }
    
    private var image: String? {
        switch setupType {
        case .btWifiR4:
            return AppAssets.scale0412
        case .lcbt:
            return AppAssets.scale0383
        default:
            return nil
        }
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
                        if let image = image {
                            Image(image)
                                .resizable()
                                .scaledToFit()
                                .frame(width: 180, height: 180)
                                .themeDropShadow()
                                .accessibilityHidden(true)
                        }

                        VStack(spacing: .spacingMD) {
                            SetupLoaderView(connectionState: state)
                                .id(state)
                                .accessibilityHidden(true)

                            ConnectionIndicatorView(
                                image: AppAssets.bluetooth,
                                isFailure: state == .failure,
                                showPulsingCircle: false
                            )
                            .accessibilityHidden(true)
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
                            .accessibilityHint(ScaleSetupStrings.A11y.tryAgainHint)

                            ButtonView(
                                text: commonStrings.support,
                                type: .inlineTextPrimary,
                                size: .large,
                                isDisabled: false,
                                action: onSupport
                            )
                            .accessibilityHint(ScaleSetupStrings.A11y.supportHint)
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
    BluetoothConnectionView(state: .loading, setupType: .btWifiR4)
        .environmentObject(Theme.shared)
}

#Preview("Success") {
    BluetoothConnectionView(state: .success, setupType: .btWifiR4)
        .environmentObject(Theme.shared)
}

#Preview("Failure w/Code") {
    BluetoothConnectionView(state: .failure, setupType: .btWifiR4, errorCode: "E42")
        .environmentObject(Theme.shared)
}
