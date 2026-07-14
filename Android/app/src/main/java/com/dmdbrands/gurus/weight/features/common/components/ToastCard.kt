package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.model.ActionButton
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme

/**
 * Stateless UI for displaying a toast card.
 * @param modifier Modifier for layout and drag offset.
 * @param toast Toast data model.
 * @param clearToast Callback to clear the toast when action is performed.
 */
@Composable
fun ToastCard(
  modifier: Modifier = Modifier,
  toast: Toast.Simple,
  clearToast: () -> Unit = {},
) {
  Card(
    modifier =
      modifier
        .testTag("toast_card")
        .statusBarsPadding()
        .padding(horizontal = 16.dp, vertical = 16.dp).cssBoxShadow(
        color = colorScheme.glow,
    offsetX = 2.dp,
    offsetY = 2.dp,
    blurRadius = 8.dp,
    spread = 0.dp,
    cornerRadius = 10.dp
      ),

    shape = RoundedCornerShape(10.dp),
    colors =
      CardDefaults.cardColors(
        // Error variant (e.g. "Couldn't delete!") uses the loading-error pink; all others neutral.
        containerColor = if (toast.isError) colorScheme.loadingError else colorScheme.toastBackground,
      ),
  ) {
    // The message/icon tint tracks the variant: red for errors, body colour otherwise.
    val contentColor = if (toast.isError) colorScheme.textError else colorScheme.textBody
    Row(
      modifier = Modifier.padding(16.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.Top,
    ) {
      toast.icon?.let {
        Icon(
          painter = painterResource(id = it),
          contentDescription = null,
          tint = contentColor,
          modifier = Modifier.testTag("toast_icon").size(24.dp),
        )
      }
      Column(
        // weight(1f) (not fillMaxWidth) so a leading icon keeps its space in the Row.
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        toast.title?.let {
          Text(
            text = it,
            modifier = Modifier.testTag("toast_title"),
            style = MeTheme.typography.heading5,
            color = contentColor,
          )
        }
        Text(
          text = toast.message,
          modifier = Modifier.testTag("toast_message"),
          style = MeTheme.typography.body2,
          color = contentColor,
        )
        toast.action?.let {
          Text(
            text = it.text.uppercase(),
            style = MeTheme.typography.button2,
            fontWeight = FontWeight.Bold,
            // Error actions ("TRY AGAIN") use the dark body colour per Figma; others the accent.
            color = if (toast.isError) colorScheme.textBody else colorScheme.primaryAction,
            modifier =
              Modifier
                .testTag("toast_action")
                .padding(vertical = 6.dp, horizontal = 2.dp)
                .clickable {
                  it.action()
                  clearToast()
                },
          )
        }
      }
    }
  }
}

@PreviewTheme
@Composable
private fun ToastCardRestoredPreview() {
  MeAppTheme {
    ToastCard(
      toast =
        Toast.Simple(
          message = "Reading restored successfully.",
          icon = AppIcons.Default.Restore,
        ),
    )
  }
}

@PreviewTheme
@Composable
private fun ToastCardErrorPreview() {
  MeAppTheme {
    ToastCard(
      toast =
        Toast.Simple(
          message = "Couldn't delete!",
          icon = AppIcons.Default.Delete,
          isError = true,
          action = ActionButton(text = "Try again"),
        ),
    )
  }
}

@Composable
fun Modifier.cssBoxShadow(
  color: Color = colorScheme.glow, // rgba(0,0,0,0.15)
  offsetX: Dp = 2.dp,
  offsetY: Dp = 2.dp,
  blurRadius: Dp = 8.dp,
  spread: Dp = 0.dp,
  cornerRadius: Dp = 10.dp
) = this.then(
  Modifier.drawBehind {
    val shadowColor = color
    val transparent = shadowColor.copy(alpha = 0f)
    val shadowSpread = spread.toPx()
    val radius = cornerRadius.toPx()
    val shadowOffsetX = offsetX.toPx()
    val shadowOffsetY = offsetY.toPx()
    val shadowBlur = blurRadius.toPx()

    drawIntoCanvas { canvas ->
      val paint = Paint().apply {
        this.color = shadowColor
        this.asFrameworkPaint().apply {
          this.isAntiAlias = true
          this.setShadowLayer(
            shadowBlur,
            shadowOffsetX,
            shadowOffsetY,
            shadowColor.toArgb()
          )
        }
      }

      val rect = Rect(
        left = shadowSpread,
        top = shadowSpread,
        right = size.width - shadowSpread,
        bottom = size.height - shadowSpread
      )

      canvas.drawRoundRect(rect.left,rect.top,rect.right,rect.bottom, radius, radius, paint)
    }
  }
)


