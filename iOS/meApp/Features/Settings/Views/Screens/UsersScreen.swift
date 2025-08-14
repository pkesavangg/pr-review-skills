//
//  UsersScreen.swift
//  meApp
//
//  Created by Lakshmi Priya on 26/06/25.
//

import SwiftUI

struct UsersScreen: View {
    @EnvironmentObject var router: Router<SettingsRoute>
    @Environment(\.appTheme) private var theme
    @StateObject private var viewModel: UsersViewModel
    @State private var focusedField: FocusField?
    let scale: Device
    let usersList: [DeviceUser]
    let lang = UsersViewStrings.self
    let loaderLang = LoaderStrings.self
    
    init(scale: Device, usersList: [DeviceUser]) {
        self.scale = scale
        self.usersList = usersList
        _viewModel = StateObject(wrappedValue: UsersViewModel(scale: scale, initialUsersList: usersList))
    }
    
    var canDisableSaveButton: Bool {
        viewModel.isLoadingUsers ||
        !viewModel.isFormValid ||
        viewModel.userNameForm.displayName.value.trimmingCharacters(in: .whitespacesAndNewlines) == (viewModel.currentDeviceUser?.name ?? "")
    }
    
    var body: some View {
        ZStack {
            VStack(alignment: .center, spacing: 0) {
                NavbarHeaderView(
                    title: lang.usersTitle,
                    leadingContent: { Image(AppAssets.chevronLeft) },
                    trailingContent: {
                        AnyView(ButtonView(
                            text: CommonStrings.save.uppercased(),
                            type: .inlineTextPrimary,
                            size: .small,
                            isDisabled: canDisableSaveButton,
                            action: {
                                saveName()
                            }
                        ))
                    },
                    onLeadingTap: { router.navigateBack() },
                    canShowBorder: true
                )
                
                ScrollView(showsIndicators: false) {
                    VStack(alignment: .leading, spacing: .spacingMD) {
                        // Current User section
                        AppInputField(
                            config: TextInputConfig(
                                label: lang.userNameLabel,
                                placeholder: viewModel.currentDeviceUser?.name ?? lang.enterUserName,
                                inputType: .text,
                                errorMessage: viewModel.displayNameError,
                                focusField: .userName
                            ),
                            value: Binding(
                                get: { viewModel.userNameForm.displayName.value },
                                set: { 
                                    viewModel.userNameForm.displayName.value = $0
                                    viewModel.setDisplayNameTouched()
                                }
                            ),
                            focusedField: $focusedField
                        ) {
                            saveName()
                        }
                        .padding(.top, .spacingLG)
                        
                        // Other Users section
                        VStack(alignment: .leading, spacing: .spacingXS) {
                            if viewModel.isLoadingUsers {
                                ProgressView()
                                    .scaleEffect(1.5)
                                    .padding(.vertical, 50)
                                    .frame(maxWidth: .infinity)
                            } else if !viewModel.otherDeviceUsersList.isEmpty {
                                Text(lang.otherUsersSection)
                                    .fontOpenSans(.heading5)
                                    .fontWeight(.bold)
                                    .foregroundColor(theme.textHeading)
                                
                                Text(lang.maxUsers)
                                    .fontOpenSans(.subHeading2)
                                    .foregroundColor(theme.textSubheading)
                                
                                DeviceUserListView(
                                    users: viewModel.otherDeviceUsersList,
                                    onDeleteUser: { user in
                                        viewModel.showDeleteUserAlert(for: user) {
                                            // Alert dismissed, no additional action needed
                                        }
                                    }
                                )
                            }
                        }
                    }
                    .padding(.horizontal, .spacingSM)
                    .frame(maxWidth: .infinity, alignment: .top)
                }
                .frame(maxHeight: .infinity, alignment: .top)
                .background(theme.backgroundSecondary.ignoresSafeArea())
                .navigationBarBackButtonHidden(true)
            }
        }
    }
    
    private func formatLastActiveTimestamp(_ timestamp: Int) -> String {
        return DateTimeTools.getFormattedDateFromTimestamp(Int64(timestamp)).toLowerCase()
    }
    
    private func saveName() {
        Task {
            // Mark field as dirty to show validation errors
            viewModel.setDisplayNameTouched()
            
            if !canDisableSaveButton {
                let trimmedName = viewModel.userNameForm.displayName.value.trimmingCharacters(in: .whitespacesAndNewlines)
                await viewModel.saveUsers(newName: trimmedName) {
                    router.navigateBack()
                }
            }
        }
    }
}

#Preview{
    UsersScreen(
        scale: Device(
            id: "preview-scale",
            accountId: "preview-account",
            nickname: "Preview Scale",
            sku: "0412",
            mac: "00:00:00:00:00:00",
            password: 3692707582,
            deviceName: "Preview Scale",
            deviceType: "bluetooth",
            broadcastId: 123456,
            broadcastIdString: "123456",
            userNumber: "1",
            protocolType: "R4",
            createdAt: "2024-01-01T00:00:00Z",
            isConnected: true,
            wifiMac: nil,
            token: "preview-token",
            bathScale: nil,
            r4ScalePreference: nil,
            metaData: nil
        ),
        usersList: [
            DeviceUser(name: "John Doe", token: "token1", lastActive: 1234567890, isBodyMetricsEnabled: true),
            DeviceUser(name: "Jane Smith", token: "token2", lastActive: 1234567800, isBodyMetricsEnabled: false)
        ]
    )
}
