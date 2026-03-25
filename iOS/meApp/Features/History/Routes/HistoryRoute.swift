import SwiftUI
enum HistoryRoute: Routable {

    case historyMonthList(month: HistoryMonth)
    case bpHistoryMonthList(month: BPHistoryMonth)
    case babyHistoryDayList(day: BabyHistoryDay)

    var body: some View {
        switch self {
        case .historyMonthList(let month):
            HistoryMonthListScreen(month: month)
        case .bpHistoryMonthList(let month):
            BPHistoryMonthListScreen(month: month)
        case .babyHistoryDayList(let day):
            BabyHistoryDayListScreen(day: day)
        }
    }
}
