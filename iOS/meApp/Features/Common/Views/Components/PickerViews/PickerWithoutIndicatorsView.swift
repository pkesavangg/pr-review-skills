//
//  PickerWithoutIndicatorsView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 10/06/25.
//
import SwiftUI

struct PickerWithoutIndicatorsView<Content: View, Selection: Hashable>: View {
    @Binding var selection: Selection
    @ViewBuilder var content: Content
    @State private var isHidden: Bool = false
    
    var body: some View {
        Picker("", selection: $selection) {
            if !isHidden {
                RemovePickerIndicator() {
                    isHidden = true
                }
            } else {
                content
            }
        }
        .pickerStyle(.wheel)
    }
}

struct RemovePickerIndicator: UIViewRepresentable {
    var result: () -> ()
    
    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .clear
        DispatchQueue.main.async {
            if let pickerView = view.pickerView {
                pickerView.backgroundColor = .clear
                if pickerView.subviews.count >= 2 {
                    pickerView.subviews[1].backgroundColor = .clear
                }
                result()
            }
        }
        return view
    }
    
    func updateUIView(_ uiView: UIView, context: Context) {}
}
