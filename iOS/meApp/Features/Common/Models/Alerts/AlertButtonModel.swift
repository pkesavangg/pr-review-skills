//
//  AlertButtonModel.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 04/06/25.
//


struct AlertButtonModel {
    let title: String
    let type: AlertButtonType
    let action: (String?) -> Void
}