import Foundation

struct Progress: Codable {
    var week: Int
    var initWeek: BathScaleOperationDTO?
    var month: Int
    var initMonth: BathScaleOperationDTO?
    var year: Int
    var initYear: BathScaleOperationDTO?
    var total: Double?
    var initWt: Double
    var currentStreak: Int
    var longestStreak: Int
    var percent: Double?
    var count: Int?
    var latest: BathScaleOperationDTO?
}
