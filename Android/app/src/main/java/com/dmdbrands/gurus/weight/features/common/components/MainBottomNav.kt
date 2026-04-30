package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.features.dashboard.enum.BOTTOM_NAV_ITEMS
import com.dmdbrands.gurus.weight.features.dashboard.string.DashboardString
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import kotlinx.coroutines.launch

/**
 * Stateless bottom navigation bar.
 *
 */
@Composable
fun MainBottomNav(
  badgeVisible: List<AppRoute> = emptyList(),
  showAppsync: Boolean,
  onOpenAppSync: () -> Unit,
  showUnreadFeedIndicator: Boolean = false,
) {
  val topBackStack = LocalNavBackStack.current
  val backStack = topBackStack.getStackForTopLevel(AppRoute.Home)
  var selectedItem by remember {
    mutableStateOf(BOTTOM_NAV_ITEMS.find { it.route == backStack.lastOrNull() } ?: BOTTOM_NAV_ITEMS[0])
  }

  val coroutineScope = rememberCoroutineScope()

  LaunchedEffect(backStack.lastOrNull()) {
    selectedItem =
      BOTTOM_NAV_ITEMS.find { it.route == backStack.lastOrNull() } ?: BOTTOM_NAV_ITEMS[0]
  }
  LocalContext.current

  NavigationBar(
    modifier = Modifier.topBorder(0.6.dp, MeTheme.colorScheme.utility),
    containerColor = MeTheme.colorScheme.primaryBackground,
  ) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(horizontal = MeTheme.spacing.xs),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      BOTTOM_NAV_ITEMS.forEachIndexed { index, item ->
        val isSelected = (selectedItem == item)
        val icon = if (isSelected && item.selectedIcon != null) item.selectedIcon else item.icon
        if (!showAppsync && item.label === DashboardString.BottomNav.appsync) return@Row
        NavigationBarItem(
          icon = {
            BadgedBox(
              badge = {
                val shouldShowBadge = item.route in badgeVisible ||
                  (item.route == AppRoute.Main.Settings && showUnreadFeedIndicator)
                if (shouldShowBadge) {
                  // Dot badge
                  Badge(
                    containerColor =MeTheme.colorScheme.danger,
                      modifier = Modifier
                        .padding(0.dp)
                        .offset(y = (18).dp)
                        .size(8.dp)
                        .clip(CircleShape)
                  )
                }
              },
            ) {
              Image(
                painter = painterResource(id = icon),
                contentDescription = null,
                colorFilter =
                  if (!isSelected || item.route == AppRoute.Main.AppSync) {
                    ColorFilter.tint(
                      MeTheme.colorScheme.textSubheading,
                    )
                  } else {
                    null
                  },
              )
            }
          },
          label = {
            Text(
              text = item.label,
              color = MeTheme.colorScheme.textSubheading,
              fontSize = 10.sp,
              fontWeight = FontWeight.W400,
              textAlign = TextAlign.Center,
            )
          },
          selected = false,
          onClick = {
            coroutineScope.launch {
              if (item.label === DashboardString.BottomNav.appsync) {
                onOpenAppSync()
              } else {
                topBackStack.addRoute(item.route, AppRoute.Home, popUpTo = AppRoute.Main.Dashboard)
                val requiredItem =
                  BOTTOM_NAV_ITEMS.find {
                    it.route == topBackStack.getStackForTopLevel(AppRoute.Home).lastOrNull()
                  }
                if (requiredItem != null && requiredItem != selectedItem) {
                  selectedItem = requiredItem
                }
              }
            }
          },
        )
      }
    }
  }
}

fun Modifier.topBorder(
  strokeWidth: Dp,
  color: Color,
): Modifier =
  this.then(
    Modifier.drawBehind {
      val px = strokeWidth.toPx()
      drawLine(
        color = color,
        start = Offset(0f, 0f),
        end = Offset(size.width, 0f),
        strokeWidth = px,
      )
    },
  )

@PreviewTheme
@Composable
fun MainBottomNavDemoScreenPreview() {
  MeAppTheme {
    MainBottomNav(
      badgeVisible = listOf(AppRoute.Main.Dashboard),
      showAppsync = false,
      showUnreadFeedIndicator = true,
      onOpenAppSync = {},
    )
  }
}
