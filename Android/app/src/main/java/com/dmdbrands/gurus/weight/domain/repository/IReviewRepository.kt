package com.dmdbrands.gurus.weight.domain.repository

import com.dmdbrands.gurus.weight.domain.model.api.review.UnifiedReviewRequest

/**
 * Repository interface for posting reviews to the unified `/v3/review/` endpoint (MOB-378).
 */
interface IReviewRepository {
    /**
     * Posts a review. Throws on network / HTTP failure.
     */
    suspend fun postReview(request: UnifiedReviewRequest)
}
