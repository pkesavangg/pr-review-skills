//
//  SignupErrorStepView.swift
//  meApp
//

import SwiftUI

struct SignupErrorStepView: View {
    @Environment(\.appTheme) private var theme
    let deviceStatuses: [(device: SignupDeviceType, status: SignupDeviceStatus)]
    let lang = SignupStrings.SignupErrorStep.self

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(spacing: .spacingLG) {
                // Error icon
                ZStack {
                    Circle()
                        .stroke(theme.statusError, lineWidth: 3)
                        .frame(width: 80, height: 80)
                    AppIconView(
                        icon: AppAssets.exclamationMark,
                        size: IconSize(width: 36, height: 36)
                    )
                    .foregroundColor(theme.statusError)
                }

                VStack(spacing: .spacingXS) {
                    Text(lang.title)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)
                        .multilineTextAlignment(.center)
                    Text(lang.subtitle)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textSubheading)
                        .multilineTextAlignment(.center)
                }

                // Per-device status cards
                VStack(spacing: .spacingSM) {
                    ForEach(deviceStatuses, id: \.device.id) { item in
                        DeviceStatusCard(deviceType: item.device, status: item.status)
                    }
                }
            }
            .padding(.horizontal, .spacingSM)
            .padding(.vertical, .spacingLG)
        }
    }
}

// MARK: - DeviceStatusCard

private struct DeviceStatusCard: View {
    @Environment(\.appTheme) private var theme
    let deviceType: SignupDeviceType
    let status: SignupDeviceStatus
    let lang = SignupStrings.SignupErrorStep.self

    var statusText: String {
        switch status {
        case .success: return lang.deviceSuccess
        case .failure: return lang.deviceFailure
        case .pending: return lang.devicePending
        }
    }

    var statusColor: Color {
        switch status {
        case .success: return theme.statusSuccess
        case .failure: return theme.statusError
        case .pending: return theme.textSubheading
        }
    }

    var body: some View {
        HStack(spacing: .spacingSM) {
            Image(deviceType.iconName)
                .resizable()
                .scaledToFit()
                .frame(width: 44, height: 44)

            VStack(alignment: .leading, spacing: 2) {
                Text(deviceType.title)
                    .fontOpenSans(.heading5)
                    .foregroundColor(theme.textHeading)
                Text(statusText)
                    .fontOpenSans(.body3)
                    .foregroundColor(statusColor)
            }

            Spacer()
        }
        .padding(.spacingSM)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(theme.backgroundPrimary)
        .cornerRadius(.spacingSM)
    }
}
