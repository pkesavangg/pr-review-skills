package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.api.review.UnifiedReviewRequest
import com.dmdbrands.gurus.weight.domain.repository.IAccountFlagRepository
import com.dmdbrands.gurus.weight.domain.repository.IReviewRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewServiceTest {

    private val reviewRepository: IReviewRepository = mockk(relaxed = true)
    private val accountFlagRepository: IAccountFlagRepository = mockk(relaxed = true)
    private lateinit var service: ReviewService

    @BeforeEach
    fun setUp() {
        mockkObject(AppLog)
        every { AppLog.d(any(), any()) } returns Unit
        every { AppLog.w(any<String>(), any<String>(), any<String>()) } returns Unit
        service = ReviewService(reviewRepository, accountFlagRepository)
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `submitReview posts the review then deletes the triggering flag`() = runTest {
        val requestSlot = slot<UnifiedReviewRequest>()
        coEvery { reviewRepository.postReview(capture(requestSlot)) } returns Unit

        service.submitReview(
            reviewType = "scale",
            status = "dismissed",
            sku = "9395",
            flagId = "flag-1",
        )

        assertThat(requestSlot.captured.reviewType).isEqualTo("scale")
        assertThat(requestSlot.captured.status).isEqualTo("dismissed")
        assertThat(requestSlot.captured.sku).isEqualTo("9395")
        assertThat(requestSlot.captured.flagId).isEqualTo("flag-1")
        coVerify(exactly = 1) { reviewRepository.postReview(any()) }
        coVerify(exactly = 1) { accountFlagRepository.deleteAccountFlag("flag-1") }
    }

    @Test
    fun `submitReview does not delete a flag when flagId is null`() = runTest {
        service.submitReview(reviewType = "scale", status = "rated", rating = 5, flagId = null)

        coVerify(exactly = 1) { reviewRepository.postReview(any()) }
        coVerify(exactly = 0) { accountFlagRepository.deleteAccountFlag(any()) }
    }

    @Test
    fun `submitReview swallows a flag-deletion failure after a successful post`() = runTest {
        coEvery { accountFlagRepository.deleteAccountFlag("flag-1") } throws RuntimeException("delete failed")

        // Should not throw — the review was already posted.
        service.submitReview(reviewType = "scale", status = "dismissed", flagId = "flag-1")

        coVerify(exactly = 1) { reviewRepository.postReview(any()) }
    }
}
