package com.dmdbrands.gurus.weight.domain.services

/**
 * Service interface for submitting reviews via the unified `/v3/review/` endpoint (MOB-378).
 */
interface IReviewService {

    /**
     * Submits a review and deletes the triggering account flag on success.
     *
     * @param reviewType `app`, `scale`, or `monitor`.
     * @param status Review status string (e.g. `rated`, `dismissed`, `exitA`).
     * @param sku Required for scale/monitor types.
     * @param rating Star rating (required unless status = `exitA`).
     * @param feedback Optional free-text feedback.
     * @param flagId The account flag to delete after successful submission.
     */
    suspend fun submitReview(
        reviewType: String,
        status: String,
        sku: String? = null,
        rating: Int? = null,
        feedback: String? = null,
        flagId: String? = null,
    )
}
