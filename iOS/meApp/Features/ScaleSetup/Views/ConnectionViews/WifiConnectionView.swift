//
//  WifiConnectionView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 14/07/25.
//

import SwiftUI

/// A reusable view that visualizes the WiFi connection lifecycle (loading, success, failure, noNetworks).
///
/// Usage:
/// ```swift
/// WifiConnectionView(state: .loading, setupType: .btWifiR4)
/// WifiConnectionView(state: .success, setupType: .btWifiR4)
/// WifiConnectionView(state: .noNetworks, setupType: .btWifiR4)
/// WifiConnectionView(state: .failure, setupType: .btWifiR4, errorCode: "E42")
/// ```
/// Pass the current `ConnectionState`; you may optionally provide `errorCode` for the failure case.
struct WifiConnectionView: View {
    // MARK: - Props
    let state: ConnectionState
    var setupType: ScaleSetupType = .btWifiR4
    /// Optional error code to display when `state == .failure`.
    var errorCode: String?
    /// True when this view is used in the Scale Settings Wi-Fi setup flow (not initial scale setup).
    var isFromSettingsFlow: Bool = false
    
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
        case .loading: return scaleSetupStrings.connectingToWifi
        case .success: return scaleSetupStrings.connectedToWifi
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
                        }
                        
                        VStack(spacing: .spacingMD) {
                            // Re-instantiate the loader every time the state changes so
                            // we don't keep the previous animation colours (e.g. red ➜ blue).
                            SetupLoaderView(connectionState: state)
                                .id(state)  // Force a fresh view when the enum value flips
                            
                            ConnectionIndicatorView(
                                image: AppAssets.wifi,
                                isFailure: (state == .failure || state == .noNetworks),
                                showPulsingCircle: false
                            )
                        }
                    }
                    
                    // Action buttons (visible only on failure)
                    if state == .failure || state == .noNetworks {
                        VStack(spacing: .spacingMD) {
                            ButtonView(
                                text: commonStrings.tryAgain,
                                type: .filledPrimary,
                                size: .large,
                                isDisabled: false,
                                action: onTryAgain
                            )
                            
                            ButtonView(
                                text: (state == .noNetworks && !isFromSettingsFlow) ? scaleSetupStrings.setupWifiLater : commonStrings.support,
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
#Preview("Loadinsg") {
    WifiConnectionView(state: .loading, setupType: .btWifiR4)
        .environmentObject(Theme.shared)
}
