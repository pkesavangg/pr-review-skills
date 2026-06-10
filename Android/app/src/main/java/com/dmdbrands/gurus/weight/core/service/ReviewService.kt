package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.api.review.UnifiedReviewRequest
import com.dmdbrands.gurus.weight.domain.repository.IAccountFlagRepository
import com.dmdbrands.gurus.weight.domain.repository.IReviewRepository
import com.dmdbrands.gurus.weight.domain.services.IReviewService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReviewService @Inject constructor(
    private val reviewRepository: IReviewRepository,
    private val accountFlagRepository: IAccountFlagRepository,
) : IReviewService {

    companion object {
        private const val TAG = "ReviewService"
    }

    override suspend fun submitReview(
        reviewType: String,
        status: String,
        sku: String?,
        rating: Int?,
        feedback: String?,
        flagId: String?,
    ) {
        val request = UnifiedReviewRequest(
            reviewType = reviewType,
            status = status,
            rating = rating,
            sku = sku,
            feedback = feedback,
            flagId = flagId,
        )
        reviewRepository.postReview(request)
        // Delete the triggering flag so the review prompt doesn't re-appear.
        if (flagId != null) {
            try {
                accountFlagRepository.deleteAccountFlag(flagId)
                AppLog.d(TAG, "Deleted account flag $flagId after review submission")
            } catch (e: Exception) {
                AppLog.w(TAG, "Review posted but flag deletion failed: $flagId", e.toString())
            }
        }
    }
}
