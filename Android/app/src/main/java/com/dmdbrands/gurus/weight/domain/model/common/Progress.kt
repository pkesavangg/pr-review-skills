package com.dmdbrands.gurus.weight.domain.model.common

import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BpmEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry

/**
 * "How am I tracking" snapshot for an account, scoped to a single product.
 * Implementations are flat siblings ([WeightProgress], [BpProgress], …) — use
 * the product-specific type directly at call sites; rely on [Progress] only
 * when a piece of code must accept any product (rare).
 */
sealed interface Progress {
  val streak: Streak
  val count: Int
}

/**
 * Weight-specific progress — the full snapshot driving the weight dashboard's
 * streak, goal, and period-change milestones (week / month / year / total).
 *
 * Field set matches the previous flat `Progress` class so call sites can be
 * migrated one accessor at a time (`currentStreak` → `streak.current`, etc).
 */
data class WeightProgress(
  override val streak: Streak = Streak(),
  override val count: Int = 0,
  val latest: Entry? = null,
  val unit: WeightUnit = WeightUnit.KG,
  val goal: Goal? = null,
  val initWt: Double = 0.0,
  val week: Double? = null,
  val month: Double? = null,
  val year: Double? = null,
  val total: Double? = null,
  val initWeek: Entry? = null,
  val initMonth: Entry? = null,
  val initYear: HistoryMonth? = null,
) : Progress

/**
 * Blood-pressure progress — minimal today (streak + entry count + latest entry).
 * Add fields as the BP dashboard grows new milestones.
 */
data class BpProgress(
  override val streak: Streak = Streak(),
  override val count: Int = 0,
  val latest: BpmEntry? = null,
) : Progress
