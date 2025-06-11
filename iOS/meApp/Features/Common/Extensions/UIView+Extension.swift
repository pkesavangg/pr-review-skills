//
//  UIView+Extension.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 10/06/25.
//

import SwiftUI

extension UIView {
    var pickerView: UIPickerView? {
        if let view = superview as? UIPickerView {
            return view
        }
        return superview?.pickerView
    }
}
