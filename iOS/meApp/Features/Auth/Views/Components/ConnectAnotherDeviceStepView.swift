//
//  ConnectAnotherDeviceStepView.swift
//  meApp
//

import SwiftUI

struct ConnectAnotherDeviceStepView: View {
    @ObservedObject var signupStore: SignupStore
    @Environment(\.appTheme) private var theme

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .center, spacing: .spacingLG) {
                VStack(spacing: .spacingXS) {
                    Text(signupStore.profileReadyTitle)
                        .fontOpenSans(.heading4)
                        .foregroundStyle(theme.textHeading)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity, alignment: .center)

                    Text(SignupStrings.ProfileReadyStep.connectAnotherSubtitle)
                        .fontOpenSans(.body2)
                        .foregroundStyle(theme.textSubheading)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity, alignment: .center)
                }

                VStack(spacing: .spacingXS) {
                    ForEach(SignupDeviceType.allCases) { deviceType in
                        let isDisabled = signupStore.disabledDeviceTypes.contains(deviceType)
                        DeviceCard(
                            deviceType: deviceType,
                            isSelected: signupStore.selectedDeviceType == deviceType,
                            isDisabled: isDisabled
                        ) {
                            if !isDisabled {
                                signupStore.selectDeviceType(deviceType)
                            }
                        }
                    }
                }
            }
        }
    }
}

#Preview {
    ConnectAnotherDeviceStepView(signupStore: SignupStore())
        .padding()
}
