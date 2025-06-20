//
//  ConnectionIndicatorView.swift
//  meApp
//
//  Created by Lakshmi Priya on 20/06/25.
//

import SwiftUI

struct ConnectionIndicatorView: View {
    let image: String
    let isFailure: Bool
    @State private var isAnimating = false
    @Environment(\.appTheme) var theme

    var body: some View {
        ZStack (alignment: .center) {
            Circle()
                .fill(isFailure ? theme.statusIconLoadingError : theme.statusIconLoading)
                .frame(width: isAnimating ? 172 : 142, height: isAnimating ? 172 : 142)
                .animation(Animation.easeInOut(duration: 1.5).repeatForever(autoreverses: true), value: isAnimating)
            
            Circle()
                .fill(isFailure ? theme.statusError : theme.brandWgPrimary)
                .frame(width: 89, height: 89)
                .padding(42)
            
            VStack(spacing: 4) {
                Image(image)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 59, height: 59)
                    .foregroundColor(theme.backgroundPrimary)
            }
        }
        .frame(width: 172, height: 172)
        .onAppear {
            isAnimating = true
        }
    }
}

#Preview {
    ConnectionIndicatorView(image: AppAssets.wifi, isFailure: false)
}

struct TestConnectedIndicatorView: View {
    var body: some View {
        VStack(spacing: 10){
            ScrollView{
                ConnectionIndicatorView(image: AppAssets.wifi, isFailure: false)
                ConnectionIndicatorView(image: AppAssets.wifi, isFailure: true)
                ConnectionIndicatorView(image: AppAssets.meLogoLight, isFailure: true)
            }
        }
    }
}

#Preview {
    TestConnectedIndicatorView()
}
