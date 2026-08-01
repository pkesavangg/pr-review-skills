// Fixture for references/compose/asset-references.md. Deliberate triggers — do not fix.
//
// Expected findings:
//   P1  resource looked up by a runtime string (getIdentifier) — invisible to R8
//   P1  hardcoded asset path / file-name string
//   P2  hardcoded user-facing string instead of stringResource
//   P2  hardcoded color / dimension where a theme token exists
//   Nit painterResource resolved inside a list item body

package com.dmdbrands.sage.feature.kettle

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun KettleCard(devices: List<Device>, iconName: String) {
    val context = LocalContext.current

    // P1 — reflective lookup: no compile check, and resource shrinking can't see
    //      the reference, so the drawable is stripped from a shrunk release build
    //      and this renders blank in production while working fine in debug.
    val iconId = context.resources.getIdentifier(iconName, "drawable", context.packageName)

    // P2 — hardcoded 0xFF… color instead of MaterialTheme.colorScheme / a token;
    //      won't follow the theme and breaks in dark mode.
    Surface(color = Color(0xFF0A84FF)) {
        // P2 — bare .dp literal where the project has a Spacing scale
        Column(modifier = Modifier.padding(16.dp)) {

            Image(painter = painterResource(iconId), contentDescription = null)

            // P1 — hardcoded asset path: unchecked by anything, fails only when
            //      this screen is opened after a rename or folder move.
            AsyncImage(
                model = "file:///android_asset/images/kettle_hero.png",
                contentDescription = null,
            )

            // P1 — hardcoded asset file name retyped at the call site
            context.assets.open("config/devices.json").use { /* … */ }

            // P2 — untranslatable literals; ship in English to every locale
            Text("Kettle is heating")
            Text("Save")

            LazyColumn {
                items(devices) { device ->
                    // Nit — painterResource re-resolves on every recomposition of
                    //       every row; hoist it or pass the @DrawableRes Int down.
                    Image(
                        painter = painterResource(R.drawable.ic_kettle),
                        contentDescription = null,
                    )
                    Text(device.name)
                }
            }
        }
    }
}
