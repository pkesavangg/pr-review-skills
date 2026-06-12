package com.dmdbrands.gurus.weight.features.common.helper.graph

import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodSummary
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment

/**
 * Shared selection-label logic for the dashboard's weight-trend header and the
 * metric-info sheet. Driving both surfaces off this helper keeps them in lockstep
 * and prevents the iOS-side regression where the metric-info sheet stamped the
 * latest entry's date even though the value shown was a period average.
 *
 * Per MA-3965 hybrid rule (Android counterpart of MA-3964):
 *  - Week / Month + latest day selected → "latest entry"
 *  - Week / Month + non-latest day selected → "day average"
 *  - Year / Total + any point selected → "month average"
 *  - No selection (any segment) → "<segment> average" (lowercased)
 *
 * The "latest day" check is data-driven, not calendar-driven: the selected
 * timestamp is compared against the most recent day in the data set that has
 * a valid entry.
 */
object GraphLabelHelper {
  /**
   * Returns the full label text for the given selection state, or the
   * no-selection segment label when [hasSelection] is false.
   *
   * @param segment which graph tab is active.
   * @param hasSelection whether a graph point is currently selected.
   * @param isLatestDaySelected when [hasSelection] is true, whether the selected
   *   point is on the most recent day in the data set. Ignored for the
   *   no-selection case and for Year/Total (which always read "month average").
   */
  fun selectionLabel(
    segment: GraphSegment,
    hasSelection: Boolean,
    isLatestDaySelected: Boolean,
  ): String = when {
    !hasSelection -> "${segment.name.lowercase()} average"
    segment == GraphSegment.WEEK || segment == GraphSegment.MONTH ->
      if (isLatestDaySelected) "latest entry" else "day average"
    else -> "month average"
  }

  /**
   * Whether the currently selected marker sits on the most recent day in [data].
   *
   * Data-driven, not calendar-driven (mirrors the hybrid DAO query, MA-3965): the
   * snapped marker timestamp is compared against the latest entry timestamp in the
   * data set. The chart snaps [markerIndex] exactly onto a plotted point's x value
   * and each day has one point, so an exact-millis match is reliable. Only
   * meaningful for WEEK/MONTH; callers gate on segment via [selectionLabel].
   *
   * @param markerIndex the selected point's x (timestamp millis as Double), or null.
   * @param data the segment's full data set (not just the visible window) so the
   *   latest-day check stays correct when the latest entry is scrolled off-screen.
   */
  fun isLatestDaySelected(markerIndex: Double?, data: List<PeriodSummary>): Boolean {
    val marker = markerIndex ?: return false
    val latestTimestamp = data.maxOfOrNull { it.getTimeStamp() } ?: return false
    return marker.toLong() == latestTimestamp
  }
}
