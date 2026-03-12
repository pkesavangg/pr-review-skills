package com.dmdbrands.gurus.weight.features.forceUpdate

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.core.net.toUri
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.forceUpdate.strings.ForceUpdateStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Shown when a certificate pin mismatch is detected.
 *
 * A mismatch means the server's certificate chain did not match the pinned intermediate CA hash —
 * this can indicate a MitM attack or that a cert rotation has occurred without a corresponding
 * app release. In either case, the user must update to a version with the new pin.
 */
@Composable
fun ForceUpdateScreen() {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MeTheme.spacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = ForceUpdateStrings.TITLE,
            style = MeTheme.typography.heading1,
            color = MeTheme.colorScheme.textHeading,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(MeTheme.spacing.md))
        Text(
            text = ForceUpdateStrings.BODY,
            style = MeTheme.typography.body1,
            color = MeTheme.colorScheme.textBody,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(MeTheme.spacing.x2l))
        AppButton(
            label = ForceUpdateStrings.UPDATE_BUTTON,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val uri = "market://details?id=${context.packageName}".toUri()
                val intent = Intent(Intent.ACTION_VIEW, uri)
                context.startActivity(intent)
            },
        )
    }
}

@PreviewTheme
@Composable
fun ForceUpdateScreenPreview() {
    MeAppTheme {
        ForceUpdateScreen()
    }
}
