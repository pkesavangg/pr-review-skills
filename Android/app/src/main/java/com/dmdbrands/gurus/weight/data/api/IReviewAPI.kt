package com.dmdbrands.gurus.weight.data.api

import com.dmdbrands.gurus.weight.domain.model.api.review.UnifiedReviewRequest
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit interface for the unified `/v3/review/` endpoint (MOB-378).
 *
 * Replaces the legacy `/review/app` and `/review/scale` endpoints. The base URL
 * already includes `/v3/`, so paths here are relative (e.g. `review/`).
 */
interface IReviewAPI {
    companion object {
        private const val REVIEW = "review/"
    }

    /**
     * Posts a review for the app, a scale, or a monitor.
     * Response body is intentionally ignored (server returns 200 with empty body or ack).
     */
    @POST(REVIEW)
    suspend fun postReview(@Body request: UnifiedReviewRequest)
}
