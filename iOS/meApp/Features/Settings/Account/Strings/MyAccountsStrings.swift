//  MyAccountsStrings.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 25/06/25.

import Foundation

/// Static strings used exclusively within the My Accounts screen.
struct MyAccountsStrings {
    /// Navigation bar title.
    static let title = "My Accounts"
    /// Button label to log into the selected account.
    static let logIntoExistingAccount = "Log Into Existing Account"
    /// Button label to create a brand-new account.
    static let createNewAccount = "Create New Account"
    /// Delete alert title.
    static let deleteAccountTitle = "Delete Account"
    /// Delete alert destructive button label.
    static let deleteAction = "Delete"
    /// Delete alert cancel button label.
    static let cancelAction = "Cancel"
    /// Prefix for delete alert message (append e-mail and '?').
    static let deleteMessagePrefix = "Are you sure you want to delete the account for "
} 
