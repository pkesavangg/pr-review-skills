package com.greatergoods.meapp.features.metricinfo

import androidx.lifecycle.ViewModel
import com.greatergoods.meapp.proto.MetricKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for the Metric Info screen.
 * Manages the selected metric segment and metric data.
 */
class MetricInfoViewModel : ViewModel() {

    private val _selectedSegment = MutableStateFlow(MetricKey.BMI)
    /**
     * Currently selected metric segment.
     */
    val selectedSegment: StateFlow<MetricKey> = _selectedSegment.asStateFlow()

    private val _metricValue = MutableStateFlow("18.3")
    /**
     * Current metric value to display.
     */
    val metricValue: StateFlow<String> = _metricValue.asStateFlow()

    private val _metricUnit = MutableStateFlow("%")
    /**
     * Current metric unit to display.
     */
    val metricUnit: StateFlow<String> = _metricUnit.asStateFlow()

    /**
     * Select a metric segment.
     */
    fun selectSegment(key: MetricKey) {
        _selectedSegment.value = key
        // TODO: Update _metricValue and _metricUnit based on segment
    }
}
