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
    @State private var isLogoAnimated = false
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        VStack(spacing: 32) {
            // Logo Section
            logoView
                .scaleEffect(isLogoAnimated ? 1 : 0.8)
                .opacity(isLogoAnimated ? 1 : 0)
                .animation(.spring(response: 0.5, dampingFraction: 0.7), value: isLogoAnimated)
            
            // Title Section
            titleView
                .opacity(isLogoAnimated ? 1 : 0)
                .offset(y: isLogoAnimated ? 0 : 20)
                .animation(.easeOut(duration: 0.5).delay(0.3), value: isLogoAnimated)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.actionPrimary)
        .preferredColorScheme(themeManager.getPreferredAppearanceMode())
        .onChange(of: colorScheme) { _, newScheme in
            themeManager.syncWithSystemColorScheme(newScheme)
        }
        .onAppear {
            withAnimation {
                isLogoAnimated = true
                themeManager.syncWithSystemColorScheme(colorScheme)
            }
        }
        LoginView()
    }
    // MARK: - UI Components
    
    private var logoView: some View {
        Image(themeManager.isDarkMode ? "meLogoLight" : "meLogoDark")
            .resizable()
            .aspectRatio(contentMode: .fit)
            .frame(width: 200, height: 200)
            .shadow(color: theme.actionPrimary.opacity(0.1), radius: 10, x: 0, y: 5)
            .onTapGesture {
                withAnimation {
                    themeManager.isDarkMode.toggle()
                }
                isLogoAnimated = false
                // Slight delay to allow state to reset before re-animating
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
                    withAnimation {
                        isLogoAnimated = true
                    }
                }
            }
    }
    private var titleView: some View {
        Text("Me.Health")
            .fontOpenSans(.heading2)
            .multilineTextAlignment(.center)
            .foregroundColor(theme.textHeading)
            .onTapGesture {
                withAnimation {
                    themeManager.isDarkMode.toggle()
                }
                isLogoAnimated = false
                // Slight delay to allow state to reset before re-animating
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
                    withAnimation {
                        isLogoAnimated = true
                    }
                }
            }
    }
}

// MARK: - Preview
#Preview {
    LoginView()
        .environmentObject(Theme.shared)
}


class LoginViewModel: ObservableObject {
   @Injector var accountService: AccountService
   
   func login() async {
       do {
           let _ = await try accountService.logIn(email: "pkesfavan@greatergoods.com", password: "123456")
           
           try await accountService.updateStreak(isStreakOn: false, streakTimestamp: "2007-10-23T00:00:00.000Z")
           let localAccount = try await accountService.getActiveAccount()
           try await accountService.updateWeightless(isWeightlessOn: true, weightlessTimestamp: "2007-10-23T00:00:00.000Z", weightlessWeight: 60.0)
           try await accountService.updateNotifications(notifications: Notifications(shouldSendEntryNotifications: false, shouldSendWeightInEntryNotifications: false))
           try await accountService.updateDashboardType(type: .dashboard12)
           try await accountService.updateDashboardMetrics(metrics: [])
           let response = try await accountService.fetchAllAccounts()
           for account in response {
               print("Account ID: \(account.dashboardSettings?.dashboardType), Email: \(account.email)")
            }
           print(response.count)
           
       } catch  {
           print("Login Error")
       }
   }
}

struct LoginView: View {
 @StateObject var viewModel = LoginViewModel()
    var body: some View {
        Text("Login View")
            .task {
                await viewModel.login()
            }
    }
}
