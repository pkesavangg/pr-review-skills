package com.dmdbrands.gurus.weight.features.common.helper.graph

import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment

/**
 * Shared selection-prefix logic for the dashboard's weight-trend header and the
 * metric-info sheet. Driving both surfaces off this helper keeps them in lockstep
 * and prevents the iOS-side regression where the metric-info sheet stamped the
 * latest entry's date even though the value shown was a period average.
 *
 * Per MA-3938:
 *  - Week / Month + point selected → null (header label hides; layout slot is
 *    preserved by the caller via Modifier.alpha(0f)). "Day average" was misleading
 *    when a past day was selected.
 *  - Year / Total + point selected → "month" (label reads "month average …" —
 *    those values genuinely are monthly averages).
 *  - No selection → the segment name lowercased ("week", "month", "year", "total").
 *
 * Callers compose the final string as "$prefix average …" — when [selectionPrefix]
 * returns null the entire label should be hidden.
 */
object GraphLabelHelper {
  fun selectionPrefix(segment: GraphSegment, hasSelection: Boolean): String? =
    when {
      !hasSelection -> segment.name.lowercase()
      segment == GraphSegment.WEEK || segment == GraphSegment.MONTH -> null
      else -> "month"
    }
}
