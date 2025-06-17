//
//  Goal.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 13/06/25.
//

// MARK: - Goal
/// A struct representing a user's goal.
struct Goal: Codable {
    let type: GoalType
    let goalWeight: Int
    let initialWeight: Int
    let goalType: GoalType
}