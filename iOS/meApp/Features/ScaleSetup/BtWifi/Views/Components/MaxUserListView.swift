//
//  MaxUserListView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 13/07/25.
//
import SwiftUI

struct MaxUserListView: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject var store: BtWifiScaleSetupStore
    let userList : [DeviceUser]
    private let lang = BtWifiScaleSetupStrings.MaxUserListViewStrings.self
    var body: some View {
        VStack {
            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: 0) {
                    VStack(spacing: .spacingXS) {
                        VStack(alignment: .leading, spacing: .spacingXS) {
                            Text(lang.title)
                                .fontOpenSans(.heading4)
                                .foregroundColor(theme.textHeading)
                            
                            Text(lang.subtitle)
                                .fontOpenSans(.body2)
                                .foregroundColor(theme.textHeading)
                            
                            DeviceUserListView(
                                users: userList,
                                onDeleteUser: { user in
                                    store.handleDeleteUser(user)
                                }
                            )
                            .padding(.top, .spacingLG)
                            
                        }
                        .padding(.top, .spacingLG)
                    }
                }
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .background(theme.backgroundSecondary)
    }
}
