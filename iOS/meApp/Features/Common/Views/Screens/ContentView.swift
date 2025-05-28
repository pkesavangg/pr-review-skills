//
//  ContentView.swift
//  meApp
//
//  Created by Barath Chittibabu on 27/05/25.
//

import SwiftUI

struct ContentView: View {
    @EnvironmentObject var themeManager: Theme
    @Environment(\.appTheme) private var theme
    @State private var selectedTheme: AppearanceMode = .system
    var body: some View {
        VStack {
            // Testing purpose it will replace by the actual content
            Text("Hello World!")
                .fontOpenSans(.heading1) // 60pt, Extra Bold
                .foregroundColor(theme.supportToastBackground)
                .onTapGesture {
                    themeManager.isDarkMode.toggle()
                }
        }
        .preferredColorScheme(themeManager.getPreferredColorScheme())
    }
}

#Preview {
    ContentView()
        .environmentObject(Theme.shared)
}
