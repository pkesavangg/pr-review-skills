//
//  ToastModifier.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 10/06/25.
//
import SwiftUI

// MARK: - SwiperView
/// A view that allows swiping between multiple views, with the ability to programmatically change the selected view.
/// This view uses a `HStack` to display the views in a horizontal scrollable area.
/// It also provides a binding to the selected index, allowing external control over which view is currently displayed.
struct SwiperView<Content: View>: View {
    @Binding var selectedIndex: Int
    let views: [Content]
    /// Keeps track of the previously rendered index so we can decide whether to animate the transition.
    @State private var previousIndex: Int
    /// Closure that decides whether horizontal padding should be applied for a given page index.
    /// Defaults to always applying padding (previous behaviour).
    private let shouldApplyHorizontalPadding: (Int) -> Bool
    
    init(
        selectedIndex: Binding<Int>,
        views: [Content],
        shouldApplyHorizontalPadding: @escaping (Int) -> Bool = { _ in true }
    ) {
        self._selectedIndex = selectedIndex
        self.views = views
        self.shouldApplyHorizontalPadding = shouldApplyHorizontalPadding
        // Initialise previousIndex with the bound value so the first render has the correct baseline.
        self._previousIndex = State(initialValue: selectedIndex.wrappedValue)
    }

    @GestureState private var dragOffset: CGFloat = 0

    var body: some View {
        GeometryReader { geometry in
            HStack(spacing: 0) {
                ForEach(0..<views.count, id: \.self) { i in
                    views[i]
                        .padding(.horizontal, shouldApplyHorizontalPadding(i) ? .spacingSM : 0)
                        .frame(width: geometry.size.width)
                }
            }
            .frame(width: geometry.size.width * CGFloat(views.count), alignment: .leading)
            .offset(x: -CGFloat(selectedIndex) * geometry.size.width + dragOffset)
            // Animate only when moving a single page; jump instantly for larger index changes.
            .animation(abs(selectedIndex - previousIndex) == 1 ? .easeInOut(duration: 0.3) : nil, value: selectedIndex)
            // Update the tracker once the view has reacted to the index change.
            .onChange(of: selectedIndex) {
                previousIndex = selectedIndex
            }
        }
    }
}

// MARK: - Swiper Testing View
struct SwiperTestingView: View {
    @State private var selectedIndex: Int = 0
    let views: [AnyView] = [
        AnyView(Text("Page 1").frame(maxWidth: .infinity, maxHeight: .infinity).background(.red)),
        AnyView(Text("Page 2").frame(maxWidth: .infinity, maxHeight: .infinity).background(.blue)),
        AnyView(Text("Page 3").frame(maxWidth: .infinity, maxHeight: .infinity).background(.green))
    ]
    var body: some View {
        VStack {
            SwiperView(selectedIndex: $selectedIndex, views: views)
            
            HStack {
                Button("Previous") {
                    if selectedIndex > 0 {
                        selectedIndex -= 1
                    }
                }
                
                Button("Next") {
                    if selectedIndex < (views.count - 1) { // Assuming there are 3 pages
                        selectedIndex += 1
                    }
                }
            }
            Spacer()
        }
    }
}

#Preview {
    SwiperTestingView()
}
