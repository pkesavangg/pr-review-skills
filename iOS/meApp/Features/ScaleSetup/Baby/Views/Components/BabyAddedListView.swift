//
//  BabyAddedListView.swift
//  meApp
//

import SwiftUI

/// "Your Baby Has Been Added!" — reuses BabyListStepView from the signup flow.
struct BabyAddedListView: View {
    @EnvironmentObject var store: BabyScaleSetupStore
    private let lang = BabyScaleSetupStrings.BabyAdded.self

    var body: some View {
        BabyListStepView(
            title: lang.title,
            addButtonText: lang.addABaby,
            babies: store.savedBabies.map {
                BabyListItem(id: UUID(uuidString: $0.id) ?? UUID(), accountID: $0.id, name: $0.name)
            },
            onTapBaby: { index in
                guard index < store.savedBabies.count else { return }
                store.editBaby(store.savedBabies[index])
            },
            onEditBaby: { index in
                guard index < store.savedBabies.count else { return }
                store.editBaby(store.savedBabies[index])
            },
            onDeleteBaby: { index in
                guard index < store.savedBabies.count else { return }
                store.confirmDeleteBabyFromList(store.savedBabies[index])
            },
            onAddBaby: {
                store.addAnotherBaby()
            }
        )
    }
}
