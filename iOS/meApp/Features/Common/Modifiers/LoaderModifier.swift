//
//  LoaderModifier.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 04/06/25.
//
import SwiftUI

/// A view modifier that displays a full-screen loading overlay with a spinner and customizable text.
///
/// Usage:
/// 1. Define your `LoaderModel` with the desired loading text.
/// 2. Bind a `@State` or `@Binding` variable to control when the loader appears.
/// 3. Attach `.presentLoader(data: $loaderData)` to any view that may require a loading indicator.
///
/// Example:
/// ```swift
/// @State private var loaderData: LoaderModel? = nil
///
/// loaderData = LoaderModel(text: "Loading...")
///
/// // When done loading
/// loaderData = nil
/// ```
///
/// Features:
/// - Full-screen dimmed background overlay
/// - Circular loading spinner styled with theme colors
/// - Bold heading text with truncation support
/// - Smooth fade-in/out animation on appear/disappear
/// - Clean layout with responsive sizing and corner radius
///
/// Notes:
/// - Overlay ignores safe areas to block interaction and focus user attention
/// - Loader will only appear when `loaderData` is not `nil`
/// - Recommended to use with `.animation` for transition smoothness


struct LoaderModifier: ViewModifier {
    @Environment(\.appTheme) private var theme
    @Binding var loaderData: LoaderModel?

    func body(content: Content) -> some View {
        ZStack {
            content
            if let loader = loaderData {
                theme.supportOverlay
                    .ignoresSafeArea()
                HStack(spacing: .spacingXS) {
                    HStack {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: theme.textHeading))
                            .scaleEffect(1)
                        Text(loader.text)
                            .fontWeight(.bold)
                            .fontOpenSans(.body1)
                            .foregroundColor(theme.textHeading)
                    }
                    .lineLimit(1)
                    .truncationMode(.tail)
                    .frame(maxWidth: 200)
                }
                .frame(width: 269, height: 88)
                .background(theme.backgroundPrimary)
                .cornerRadius(.radiusSM)
            }
        }
        .animation(.easeInOut(duration: 0.2), value: loaderData != nil)
    }
}
