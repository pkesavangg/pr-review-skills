//
//  AlertModel.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 04/06/25.
//


struct AlertModel {
    var title: String
    var message: String?
    var buttons: [AlertButtonModel]
    var inputField: AlertInputField? = nil
    
    init(title: String, 
         message: String? = nil, 
         buttons: [AlertButtonModel],
         inputField: AlertInputField? = nil) {
        self.title = title
        self.message = message
        self.buttons = buttons
        self.inputField = inputField
    }
}