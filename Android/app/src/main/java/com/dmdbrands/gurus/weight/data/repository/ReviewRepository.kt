package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.api.IReviewAPI
import com.dmdbrands.gurus.weight.domain.model.api.review.UnifiedReviewRequest
import com.dmdbrands.gurus.weight.domain.repository.IReviewRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReviewRepository @Inject constructor(
    private val reviewAPI: IReviewAPI,
) : IReviewRepository {

    companion object {
        private const val TAG = "ReviewRepository"
    }

    override suspend fun postReview(request: UnifiedReviewRequest) {
        try {
            reviewAPI.postReview(request)
            AppLog.i(TAG, "Review posted — type=${request.reviewType} status=${request.status}")
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to post review type=${request.reviewType}", e)
            throw e
        }
    }
}
