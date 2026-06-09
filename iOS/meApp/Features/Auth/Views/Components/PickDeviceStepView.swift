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
                    .foregroundStyle(theme.textHeading)

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

                Text(lang.supportingNote)
                    .fontOpenSans(.body3)
                    .foregroundStyle(theme.textSubheading)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }
}

// MARK: - DeviceCard

private struct DeviceCard: View {
    @Environment(\.appTheme) private var theme
    let deviceType: SignupDeviceType
    let isSelected: Bool
    let isDisabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(alignment: .center, spacing: .spacingSM) {
                Image(deviceType.iconName)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 75, height: 75)
                    .opacity(isDisabled ? 0.4 : 1)

                VStack(alignment: .leading, spacing: 0) {
                    Text(deviceType.title)
                        .fontOpenSans(.heading5)
                        .foregroundStyle(isDisabled ? theme.textSubheading : theme.textHeading)
                    Text(isDisabled ? SignupStrings.PickDeviceStep.alreadyAdded : deviceType.subtitle.lowercased())
                        .fontOpenSans(.body3)
                        .foregroundStyle(theme.textSubheading)
                }

                Spacer()

                Image(systemName: isSelected ? "circle.inset.filled" : "circle")
                    .foregroundStyle(isDisabled ? theme.textSubheading.opacity(0.4) : (isSelected ? theme.actionPrimary : theme.textSubheading))
                    .font(.system(size: 22))
            }
            .padding(.spacingSM)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(theme.backgroundPrimary)
            .cornerRadius(.spacingSM)
        }
        .buttonStyle(.plain)
        .disabled(isDisabled)
    }
}

#Preview {
    PickDeviceStepView(signupStore: SignupStore())
        .padding()
}
