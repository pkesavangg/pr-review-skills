//
//  DeviceUserListView.swift
//  meApp
//
//  Created by Assistant on 01/01/25.
//

import SwiftUI

struct DeviceUserListView: View {
    let users: [DeviceUser]
    let onDeleteUser: (DeviceUser) -> Void
    var trashIconColorRed = true
    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(spacing: 2) {
            ForEach(Array(users.enumerated()), id: \.element.name) { idx, user in
                ListItemView(
                    title: user.name,
                    subtitle: "last active on \(formatLastActive(user.lastActive))",
                    trailing: Button(action: {
                        onDeleteUser(user)
                    }) {
                        AppIconView(icon: AppAssets.trash, size: IconSize(width: 18, height: 20))
                            .foregroundColor(trashIconColorRed ? theme.statusError: theme.actionPrimary)
                    },
                    rowHeight: 56,
                    onTap: {
                        onDeleteUser(user)
                    },
                    verticalPadding: .spacingXS
                )
                if idx < users.count - 1 {
                    Divider()
                        .background(theme.statusUtilityPrimary)
                }
            }
        }
        .background(theme.backgroundPrimary)
        .cornerRadius(.radiusXS)
    }

    private func formatLastActive(_ timestamp: Int) -> String {
        (DateTimeTools.getFormattedDateFromTimestamp(Int64(timestamp)).toLowerCase())
    }
}

#Preview {
    DeviceUserListView(
        users: Array(repeating: DeviceUser(
            name: "User Name",
            token: "dummy-token",
            lastActive: 1_711_112_000,
            isBodyMetricsEnabled: true
        ), count: 3),
        onDeleteUser: { _ in }
    )
    .environmentObject(Theme.shared)
} 
