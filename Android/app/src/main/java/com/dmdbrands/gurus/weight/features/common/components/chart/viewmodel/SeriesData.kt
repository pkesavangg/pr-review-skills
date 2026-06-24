package com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel

/**
 * A single line series: parallel x/y value lists.
 * Used by product ViewModels to push data to Vico producers.
 */
data class SeriesData(
  val xValues: List<Long>,
  val yValues: List<Double>,
)
