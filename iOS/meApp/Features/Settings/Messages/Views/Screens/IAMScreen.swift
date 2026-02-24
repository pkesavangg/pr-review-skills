//
//  IAMScreen.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 28/07/25.
//

import Combine
import ggInAppMessagingPackage
import SwiftUI

struct IAMScreen: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject var router: Router<SettingsRoute>
    @StateObject private var viewModel = IAMScreenViewModel()
    
    var body: some View {
        VStack(spacing: 0) {
            FeedListView(onClickBack: {
                router.navigateBack()
            }, onClickRefresh: {
                viewModel.refreshFeed()
            })
        }
        .navigationBarBackButtonHidden(true)
    }
}

#Preview {
    IAMScreen()
}
