package com.dmdbrands.gurus.weight.data.api

import com.dmdbrands.gurus.weight.domain.model.api.baby.BabyRequest
import com.dmdbrands.gurus.weight.domain.model.api.baby.BabyResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Retrofit API for Baby Profile CRUD.
 *
 * Paths are relative to the configured base URL, which already includes the
 * `/v3/` prefix (mirrors [IUserAPI] using `account/...` and [EntryApi] using
 * `operation/r4`).
 *
 * Scope (per the Baby App audit): create / list / update / delete only.
 * Single-baby GET, accounts/permissions, and invitation endpoints are excluded.
 */
interface IBabyAPI {
    companion object {
        private const val BABY = "baby/"
        private const val BABY_ID = "baby/{babyId}"
    }

    /** Creates a baby; server auto-adds `"baby"` to the account's productTypes. */
    @POST(BABY)
    suspend fun createBaby(@Body request: BabyRequest): BabyResponse

    /** Lists all babies the active account can access. */
    @GET(BABY)
    suspend fun getBabies(): List<BabyResponse>

    /** Updates a baby (owner only). */
    @PUT(BABY_ID)
    suspend fun updateBaby(
        @Path("babyId") babyId: String,
        @Body request: BabyRequest,
    ): BabyResponse

    /** Deletes a baby; server auto-removes `"baby"` from productTypes if it was the last one. */
    @DELETE(BABY_ID)
    suspend fun deleteBaby(@Path("babyId") babyId: String)
}
