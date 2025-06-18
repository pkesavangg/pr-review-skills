//
//  ContentViewState.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 18/06/25.
//


/// Represents the different UI states that the root `ContentView` can be in.
///
/// - initializing:   The app is performing start-up tasks.
/// - dashboard:      The user is authenticated and should see the main app content.
/// - landing:        The user is unauthenticated and should see the onboarding/login flow.
enum ContentViewState {
    case initializing
    case dashboard
    case landing
}
