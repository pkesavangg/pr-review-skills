//
//  AppleHealthIntegrationState.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 26/06/25.
//


enum AppleHealthIntegrationState {
    case notConnected
    case permissionsAllowed
    case permissionsNotAllowed
    case integrationComplete
    case integrationFailed
    case userConflict
}
