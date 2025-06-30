//
//  DashboardView.swift
//  meApp
//
//  Created by Lakshmi Priya on 30/06/25.
//

import SwiftUI

struct DashboardScreen: View {
    @Environment(\.appTheme) private var theme
    var body: some View {
        VStack{
            ScrollView{
                NavbarHeaderView<EmptyView, EmptyView>(canShowBorder: false)
                WeightTrendView()
                    .frame(height: 490)
                    .padding(.top, .spacingLG)
                
                Spacer()
            }
        }
        .ignoresSafeArea(.all)
        .background(theme.backgroundSecondary)

    }
}
