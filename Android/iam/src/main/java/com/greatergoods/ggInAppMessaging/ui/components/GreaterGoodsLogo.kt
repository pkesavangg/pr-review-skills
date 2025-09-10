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
import com.greatergoods.ggInAppMessaging.core.utilities.IAMLogger
import com.greatergoods.ggInAppMessaging.features.common.AppIcon
import com.greatergoods.ggInAppMessaging.theme.IamTheme
import com.greatergoods.ggInAppMessaging.ui.viewmodel.FeedLandingIntent

/**
 * Reusable composable for the Greater Goods logo
 * Displays the GG logo at the bottom of the screen
 * Uses intents for click handling following MVI pattern
 */
@Composable
fun GreaterGoodsLogo(
  onIntent: (FeedLandingIntent) -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
      .fillMaxWidth()
      .height(71.dp)
      .padding(horizontal = 16.dp, vertical = 16.dp)
  ) {
    AppIcon(
      id = R.drawable.greater_goods_logo, // You'll need to add this drawable
      contentDescription = "Greater Goods Logo",
      tintColor = IamTheme.colors.subSecondaryBackground,
      modifier = Modifier.size(width = 140.dp, height = 52.dp),
      onClick = {
        IAMLogger.d("GreaterGoodsLogo", "Logo clicked, dispatching OnGreaterGoodsLogoClick intent")
        onIntent(FeedLandingIntent.OnGreaterGoodsLogoClick)
      },
    )
  }
}
