//
//  IAMScreen.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 28/07/25.
//

import SwiftUI
import Combine
import ggInAppMessagingPackage

struct IAMScreen: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject var router: Router<SettingsRoute>
    
    var body: some View {
        VStack(spacing: 0) {
            FeedListView() {
                router.navigateBack()
            }
        }
        .navigationBarBackButtonHidden(true)
    }
}

#Preview {
    IAMScreen()
}
