package com.dmdbrands.gurus.weight.domain.services

import com.dmdbrands.gurus.weight.domain.model.common.GraphData
import com.dmdbrands.gurus.weight.domain.model.common.GroupedHistory
import com.dmdbrands.gurus.weight.domain.model.common.HistoryDetail
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import kotlinx.coroutines.flow.Flow

/**
 * Service for history data across all product types.
 * Takes [ProductSelection] and returns sealed types — caller doesn't need to know
 * which repository function runs internally.
 * accountId is set via [setAccountId] from LoadingScreenViewModel.
 */
interface IHistoryService {

    /** Current account ID, set from LoadingScreenViewModel during startup. */
    val accountId: String?

    /** Called from LoadingScreenViewModel.loadData() — same pattern as other services. */
    fun setAccountId(accountId: String)

    fun getGroupedHistory(product: ProductSelection): Flow<GroupedHistory>

    fun getDetail(product: ProductSelection, key: String): Flow<HistoryDetail>

    fun getMonthlyGraphData(product: ProductSelection): Flow<GraphData>

    fun getDailyGraphData(product: ProductSelection): Flow<GraphData>
}
