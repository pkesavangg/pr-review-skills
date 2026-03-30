//
//  PickDeviceStepView.swift
//  meApp
//

import SwiftUI

struct PickDeviceStepView: View {
    @ObservedObject var signupStore: SignupStore
    @Environment(\.appTheme) private var theme
    let lang = SignupStrings.PickDeviceStep.self

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: .spacingLG) {
                Text(lang.title)
                    .fontOpenSans(.heading4)
                    .foregroundColor(theme.textHeading)

                ForEach(SignupDeviceType.allCases) { deviceType in
                    DeviceCard(
                        deviceType: deviceType,
                        isSelected: signupStore.selectedDeviceType == deviceType
                    ) {
                        signupStore.selectDeviceType(deviceType)
                    }
                }
            }
        }
    }
}

// MARK: - DeviceCard

private struct DeviceCard: View {
    @Environment(\.appTheme) private var theme
    let deviceType: SignupDeviceType
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(alignment: .center, spacing: .spacingSM) {
                Image(deviceType.iconName)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 75, height: 75)

                VStack(alignment: .leading, spacing: 0) {
                    Text(deviceType.title)
                        .fontOpenSans(.heading5)
                        .foregroundColor(theme.textHeading)
                    Text(deviceType.subtitle.lowercased())
                        .fontOpenSans(.body3)
                        .foregroundColor(theme.textSubheading)
                }

                Spacer()

                Image(systemName: isSelected ? "circle.inset.filled" : "circle")
                    .foregroundColor(isSelected ? theme.actionPrimary : theme.textSubheading)
                    .font(.system(size: 22))
            }
            .padding(.spacingSM)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(theme.backgroundPrimary)
            .cornerRadius(.spacingSM)
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    PickDeviceStepView(signupStore: SignupStore())
        .padding()
}
