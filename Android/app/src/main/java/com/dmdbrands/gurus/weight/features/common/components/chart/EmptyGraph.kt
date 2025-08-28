package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.DeviceType
import com.dmdbrands.gurus.weight.features.common.helper.getDeviceType
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil.intervalCount
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.fixed
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberEnd
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.BaseAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import java.util.Calendar

@Composable
fun EmptyGraph(segment: GraphSegment) {
  val currentDeviceType = getDeviceType()
  val chartHeight = if (currentDeviceType == DeviceType.Tablet)
    400.dp else 300.dp
  val modelProducer = remember { CartesianChartModelProducer() }
  LaunchedEffect(modelProducer) {
    modelProducer.runTransaction {
      lineSeries {
        series(0)
      }
    }
  }
  val currentTimeStamp = Calendar.getInstance().timeInMillis
  val XRangeStart = GraphUtil.getStartRange(segment, currentTimeStamp)
  val XRangeEnd = GraphUtil.getEndRange(segment, currentTimeStamp)
  val layer = rememberLineCartesianLayer(
    rangeProvider = CartesianLayerRangeProvider.fixed(
      minX = XRangeStart?.toDouble(),
      maxX = XRangeEnd?.toDouble(),
      minY = 2.0,
    ),
  )
  val chart = rememberCartesianChart(
    layer,
    bottomAxis = bottomAxis(segment),
    endAxis =
      VerticalAxis.rememberEnd(
        size = BaseAxis.Size.fixed(40.dp),
        line = rememberAxisLineComponent(),
        guideline = null,
        label = null,
        tickLength = 0.dp,
      ),
    visibleLabelsCount = segment.intervalCount(),
    getXStep = {
      GraphUtil.calculateXStep(
        segment,
      )
    },
  )
  CartesianChartHost(
    modifier = Modifier.height(chartHeight),
    chart = chart,
    modelProducer = modelProducer,
  )
}
