import SwiftUI
enum HistoryRoute: Routable {

    case historyMonthList(month: HistoryMonth)

    var body: some View {
        switch self {
        case .historyMonthList(let month):
            HistoryMonthListScreen(month: month)
        }
    }
}
