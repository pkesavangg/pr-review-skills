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
    @StateObject private var userNameForm = UserNameForm()
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
        !userNameForm.displayName.isValid ||
        userNameForm.displayName.value.trimmingCharacters(in: .whitespacesAndNewlines) == (viewModel.currentDeviceUser?.name ?? "")
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
                                errorMessage: userNameForm.getError(for: userNameForm.displayName),
                                focusField: .userName
                            ),
                            value: Binding(
                                get: { userNameForm.displayName.value },
                                set: { userNameForm.displayName.value = $0 }
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
        .onAppear {
            Task {
                await viewModel.loadUsers()
                await MainActor.run {
                    let currentName = viewModel.currentDeviceUser?.name ?? ""
                    userNameForm.setDisplayName(currentName)
                    let scaleUsers = viewModel.otherDeviceUsersList.map { deviceUser in
                        ScaleUser(name: deviceUser.name, token: deviceUser.token)
                    }
                    userNameForm.updateUserList(scaleUsers)
                }
            }
        }
    }
    
    private func formatLastActiveTimestamp(_ timestamp: Int) -> String {
        return DateTimeTools.getFormattedDateFromTimestamp(Int64(timestamp)).toLowerCase()
    }
    
    private func saveName() {
        Task {
            if !canDisableSaveButton {
                let trimmedName = userNameForm.displayName.value.trimmingCharacters(in: .whitespacesAndNewlines)
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
