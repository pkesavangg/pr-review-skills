package com.dmdbrands.gurus.weight.domain.model.common

/**
 * Consecutive-day logging streak for a single product.
 *
 * @property current Days in the current streak, counted back from today with a
 *   one-day grace (today missing is fine if yesterday has an entry). Matches the
 *   algorithm in `EntryServiceHelper.computeCurrentStreakFromDates`.
 * @property longest Highest [current] value ever achieved on the account.
 */
data class Streak(
  val current: Int = 0,
  val longest: Int = 0,
)
