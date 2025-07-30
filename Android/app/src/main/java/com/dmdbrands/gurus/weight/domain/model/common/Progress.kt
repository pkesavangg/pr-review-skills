package com.dmdbrands.gurus.weight.domain.model.common

import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry

data class Progress(
  val latest: Entry? = null,
  val goal: Goal? = null,
  val currentStreak: Int = 0,
  val longestStreak: Int = 0,
  val count: Int = 0,
  val initWt: Double = 0.0,
  val week: Double = 0.0,
  val month: Double = 0.0,
  val year: Double = 0.0,
  val total: Double = 0.0,
  val initWeek: Entry? = null,
  val initMonth: Entry? = null,
  val initYear: HistoryMonth? = null
)
