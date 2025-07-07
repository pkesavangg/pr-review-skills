/// Summary of progress for dashboard (customize as needed)
struct ProgressSummary {
    let totalEntries: Int
    let streak: Int
    static let empty = ProgressSummary(totalEntries: 0, streak: 0)
}
