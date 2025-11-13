package com.dmdbrands.gurus.weight.domain.model.common

import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry

data class Progress(
  val latest: Entry? = null,
  val unit: WeightUnit = WeightUnit.KG,
  val goal: Goal? = null,
  val currentStreak: Int = 0,
  val longestStreak: Int = 0,
  val count: Int = 0,
  val initWt: Double = 0.0,
  val week: Double? = null,
  val month: Double? = null,
  val year: Double? = null,
  val total: Double? = null,
  val initWeek: Entry? = null,
  val initMonth: Entry? = null,
  val initYear: HistoryMonth? = null
)
