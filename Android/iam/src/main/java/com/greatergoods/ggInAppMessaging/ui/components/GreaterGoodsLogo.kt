package com.greatergoods.ggInAppMessaging.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greatergoods.ggInAppMessaging.R
import com.greatergoods.ggInAppMessaging.features.common.AppIcon
import com.greatergoods.ggInAppMessaging.theme.IamTheme

/**
 * Reusable composable for the Greater Goods logo
 * Displays the GG logo at the bottom of the screen
 */
@Composable
fun GreaterGoodsLogo(
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(71.dp)
      .padding(horizontal = 16.dp, vertical = 16.dp),
    contentAlignment = Alignment.Center,
  ) {
    AppIcon(
      id = R.drawable.greater_goods_logo, // You'll need to add this drawable
      contentDescription = "Greater Goods Logo",
      tintColor = IamTheme.colors.subSecondaryBackground,
      modifier = Modifier.size(width = 140.dp, height = 52.dp),
    )
  }
}
