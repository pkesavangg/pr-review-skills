//
//  BtWifiSetupErrorStateView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 15/07/25.
//
import SwiftUI

/// A view representing the Bluetooth + Wi-Fi setup error state.
///
/// Use this view when a failure occurs during the Bluetooth-WiFi setup process,
/// such as pairing or firmware update issues.
///
/// - Parameters:
///   - errorCode: An optional string representing the error code to be displayed, if any.
///   - onTryAgain: A closure called when the user taps the **Try Again** button.
///   - onSupport: A closure called when the user taps the **Support** button.
///
/// ### Example
/// ```swift
/// BtWifiSetupErrorStateView(
///     errorCode: "E42",
///     onTryAgain: { retrySetup() },
///     onSupport: { openSupportPage() }
/// )
/// ```
///
/// This view displays:
/// - A title indicating the failure.
/// - An optional error code message.
/// - A warning indicator.
/// - Action buttons for retrying or contacting support.
struct BtWifiSetupErrorStateView: View {
    // MARK: - Properties
    /// The heading shown at the top of the error view.
    var title: String = BtWifiScaleSetupStrings.BtWifiSetupErrorStateViewStrings.updateFailed
    /// Optional error code to show below the title.
    var errorCode: String? = nil

    /// Triggered when the user taps the **Try Again** button.
    var onTryAgain: () -> Void = {}

    /// Triggered when the user taps the **Support** button.
    var onSupport: () -> Void = {}

    @Environment(\.appTheme) private var theme
    private let scaleSetupStrings = ScaleSetupStrings.self
    private let commonStrings = CommonStrings.self

    /// Whether to show the error code label.
    private var showErrorCode: Bool {
        errorCode != nil && !errorCode!.isEmpty
    }

   

    // MARK: - Body

    var body: some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(spacing: .spacingMD) {
                    
                    // Title and error code
                    VStack(spacing: .spacingXS) {
                        Text(title)
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

                    // Failure visual indicator
                    VStack {
                        ConnectionIndicatorView(
                            image: AppAssets.exclamationMarkSimple,
                            isFailure: true,
                            showPulsingCircle: true,
                            customImageSize: CGSize(width: 8, height: 35),
                            showSmallCircleOverride: true
                        )
                        .padding(.bottom, 180)
                    }

                    // Retry and support buttons
                    VStack(spacing: .spacingMD) {
                        ButtonView(
                            text: commonStrings.tryAgain,
                            type: .filledPrimary,
                            size: .large,
                            isDisabled: false,
                            action: onTryAgain
                        )

                        ButtonView(
                            text: commonStrings.support,
                            type: .inlineTextPrimary,
                            size: .large,
                            isDisabled: false,
                            action: onSupport
                        )
                    }
                    .padding(.top, .spacingXL)
                }
                .frame(minHeight: geometry.size.height)
                .frame(maxWidth: .infinity, alignment: .center)
            }
        }
    }
}
