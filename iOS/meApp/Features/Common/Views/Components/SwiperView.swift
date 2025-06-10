//
//  ToastModifier.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 10/06/25.
//
import SwiftUI

// MARK: - SwiperView
/// A view that allows swiping between multiple views, with the ability to programmatically change the selected view.
/// This view uses a `TabView` with a page style to enable swiping between views.
/// It also provides a binding to the selected index, allowing external control over which view is currently displayed.
struct SwiperView<Content: View>: View {
    @Binding var selectedIndex: Int
    let views: [Content]
    
    init(
        selectedIndex: Binding<Int>,
        views: [Content],
    ) {
        self._selectedIndex = selectedIndex
        self.views = views
    }
    
    var body: some View {
        VStack(spacing: 0) {
            TabView(selection: $selectedIndex) {
                ForEach(0..<views.count, id: \.self) { index in
                    views[index]
                        .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .animation(.easeInOut, value: selectedIndex)
            .simultaneousGesture(DragGesture())
            .onAppear {
                // Disable swipe gesture using UIKit
                UIScrollView.appearance().isScrollEnabled = false
            }
            .onDisappear {
                // Re-enable swipe gesture when view disappears
                UIScrollView.appearance().isScrollEnabled = true
            }
        }
    }
}

// MARK: - Swiper Testing View
struct SwiperTestingView: View {
    @State private var selectedIndex: Int = 0
    var body: some View {
        VStack {
            SwiperView(selectedIndex: $selectedIndex, views: [
                Text("Page 1").frame(maxWidth: .infinity, maxHeight: .infinity).background(.red),
                Text("Page 2").frame(maxWidth: .infinity, maxHeight: .infinity).background(.blue),
                Text("Page 3").frame(maxWidth: .infinity, maxHeight: .infinity).background(.green)
            ])
            
            HStack {
                Button("Previous") {
                    if selectedIndex > 0 {
                        selectedIndex -= 1
                    }
                }
                
                
                Button("Next") {
                    if selectedIndex < 2 { // Assuming there are 3 pages
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
