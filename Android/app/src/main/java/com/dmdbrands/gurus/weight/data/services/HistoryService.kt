package com.dmdbrands.gurus.weight.data.services

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.common.GraphData
import com.dmdbrands.gurus.weight.domain.model.common.GroupedHistory
import com.dmdbrands.gurus.weight.domain.model.common.HistoryDetail
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBabySummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBpmSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.WeightSnapshotPoint
import com.dmdbrands.gurus.weight.domain.repository.IHistoryRepository
import com.dmdbrands.gurus.weight.domain.services.IHistoryService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Implementation of [IHistoryService].
 * Routes by [ProductSelection] to the correct [IHistoryRepository] function
 * and wraps the result in sealed [GroupedHistory] / [HistoryDetail].
 * accountId is set via [setAccountId] from LoadingScreenViewModel.
 */
class HistoryService @Inject constructor(
    private val historyRepository: IHistoryRepository,
) : IHistoryService {

    companion object {
        private const val TAG = "HistoryService"
    }

    private var _accountId: String? = null
    override val accountId: String? get() = _accountId

    override fun setAccountId(accountId: String) {
        AppLog.d(TAG, "setAccountId: $accountId")
        _accountId = accountId
    }

    override fun getGroupedHistory(product: ProductSelection): Flow<GroupedHistory> {
        val acctId = requireNotNull(_accountId) { "accountId not set" }
        AppLog.d(TAG, "getGroupedHistory: ${product.productType}")
        return when (product) {
            is ProductSelection.MyWeight -> historyRepository.getWeightMonthlyHistory(acctId)
                .map { GroupedHistory.Weight(it) }

            is ProductSelection.BloodPressure -> historyRepository.getBpmMonthlyHistory(acctId)
                .map { GroupedHistory.BloodPressure(it) }

            is ProductSelection.Baby -> historyRepository.getBabyWeeklyHistory(acctId, product.profile.id)
                .map { GroupedHistory.Baby(it) }
        }
    }

    override fun getDetail(product: ProductSelection, key: String): Flow<HistoryDetail> {
        val acctId = requireNotNull(_accountId) { "accountId not set" }
        AppLog.d(TAG, "getDetail: ${product.productType}, key=$key")
        return when (product) {
            is ProductSelection.MyWeight -> historyRepository.getWeightMonthDetail(acctId, key)
                .map { HistoryDetail.Weight(it) }

            is ProductSelection.BloodPressure -> historyRepository.getBpmMonthDetail(acctId, key)
                .map { HistoryDetail.BloodPressure(it) }

            is ProductSelection.Baby -> historyRepository.getBabyDayDetail(acctId, product.profile.id, key)
                .map { HistoryDetail.Baby(it) }
        }
    }

    override fun getMonthlyGraphData(product: ProductSelection): Flow<GraphData> {
        val acctId = requireNotNull(_accountId) { "accountId not set" }
        AppLog.d(TAG, "getMonthlyGraphData: ${product.productType}")
        return when (product) {
            is ProductSelection.MyWeight -> historyRepository.getWeightMonthlyGraphData(acctId)
                .map { GraphData.Weight(it) }

            is ProductSelection.BloodPressure -> historyRepository.getBpmMonthlyGraphData(acctId)
                .map { GraphData.BloodPressure(it) }

            is ProductSelection.Baby -> historyRepository.getBabyMonthlyGraphData(acctId, product.profile.id)
                .map { GraphData.Baby(it) }
        }
    }

    override fun getDailyGraphData(product: ProductSelection): Flow<GraphData> {
        val acctId = requireNotNull(_accountId) { "accountId not set" }
        AppLog.d(TAG, "getDailyGraphData: ${product.productType}")
        return when (product) {
            is ProductSelection.MyWeight -> historyRepository.getWeightDailyGraphData(acctId)
                .map { GraphData.Weight(it) }

            is ProductSelection.BloodPressure -> historyRepository.getBpmDailyGraphData(acctId)
                .map { GraphData.BloodPressure(it) }

            is ProductSelection.Baby -> historyRepository.getBabyDailyGraphData(acctId, product.profile.id)
                .map { GraphData.Baby(it) }
        }
    }

    override fun getWeightSnapshotGraphData(): Flow<List<WeightSnapshotPoint>> {
        val acctId = requireNotNull(_accountId) { "accountId not set" }
        return historyRepository.getWeightSnapshotGraphData(acctId)
    }

    override fun getBpmSnapshotGraphData(): Flow<List<PeriodBpmSummary>> {
        val acctId = requireNotNull(_accountId) { "accountId not set" }
        return historyRepository.getBpmSnapshotGraphData(acctId)
    }

    override fun getBabySnapshotGraphData(babyProfileId: String): Flow<List<PeriodBabySummary>> {
        val acctId = requireNotNull(_accountId) { "accountId not set" }
        return historyRepository.getBabySnapshotGraphData(acctId, babyProfileId)
    }

    override fun getBabyDailyGraphData(babyProfileId: String): Flow<List<PeriodBabySummary>> {
        val acctId = requireNotNull(_accountId) { "accountId not set" }
        return historyRepository.getBabyDailyGraphData(acctId, babyProfileId)
    }

    override fun getBabyMonthlyGraphData(babyProfileId: String): Flow<List<PeriodBabySummary>> {
        val acctId = requireNotNull(_accountId) { "accountId not set" }
        return historyRepository.getBabyMonthlyGraphData(acctId, babyProfileId)
    }
}
