package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.features.common.helper.BabyPercentileHelper
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.baby.BabyMetric

/**
 * Builds the crosshair label formatter used by the Baby chart — returns the CDC
 * percentile (e.g. `"50%"`, `"< 1%"`, `"> 99%"`) for the hovered point, or `null`
 * when the baby's profile isn't yet loaded or the percentile can't be computed.
 *
 * Plugged into Vico's [com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker]
 * via `horizontalLabelFormatter`. The marker receives per-series Y values and the
 * hovered X (chart-coordinate epoch millis) each frame, so the age-dependent
 * percentile tracks as the user scrubs across the chart.
 */
@Composable
fun rememberBabyPercentileLabel(
  profile: BabyProfile?,
  metric: BabyMetric,
): (List<List<Double>>, Double) -> CharSequence? {
  val sex = profile?.sex
  val birthDateMillis = remember(profile?.birthdate) {
    profile?.birthdate?.let { DateTimeConverter.isoToTimestamp(it) }
  }
  val measurementType = when (metric) {
    BabyMetric.WEIGHT -> BabyPercentileHelper.MeasurementType.WEIGHT
    BabyMetric.HEIGHT -> BabyPercentileHelper.MeasurementType.LENGTH
  }
  return remember(sex, birthDateMillis, measurementType) {
    { yValues: List<List<Double>>, xMillis: Double ->
      // Baby chart stacks the 7 percentile bands as earlier layers and the real
      // data line last — match DefaultCartesianMarker's crosshair-Y pick by taking
      // the LAST series' value, not the first. Otherwise the percentile shown would
      // correspond to the 5th-percentile band curve, not the baby.
      // Assumes real-data series is the LAST layer — see BabyDashboardViewModel.activeSeriesFor
      val rawValue = yValues.lastOrNull()?.lastOrNull()
      when {
        rawValue == null || birthDateMillis == null -> null
        else -> {
          // yValues are in chart display units (lbs / inches). Convert back to
          // CDC source units (decigrams / mm) before percentile lookup.
          val cdcValue = when (measurementType) {
            BabyPercentileHelper.MeasurementType.WEIGHT -> ConversionTools.convertLbToDecigrams(rawValue).toDouble()
            BabyPercentileHelper.MeasurementType.LENGTH -> ConversionTools.convertInchesToMm(rawValue).toDouble()
          }
          val percentile = BabyPercentileHelper.calcPercentile(
            sex = sex,
            birthDateMillis = birthDateMillis,
            value = cdcValue,
            type = measurementType,
            entryTimestampMillis = xMillis.toLong(),
          )
          BabyPercentileHelper.formatPercentileNumber(percentile, sex)?.let { "$it%" }
        }
      }
    }
  }
}
