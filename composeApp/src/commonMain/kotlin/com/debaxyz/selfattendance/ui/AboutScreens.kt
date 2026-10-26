@file:OptIn(ExperimentalMaterial3Api::class)

package com.debaxyz.selfattendance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.debaxyz.selfattendance.platform.rememberAppVersion
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/* ============================================================
 *  ABOUT + OPEN SOURCE LICENSES
 *  Self-contained: no ViewModel changes needed. Colours come from
 *  MaterialTheme, so both dialogs follow the dynamic light/dark theme.
 * ============================================================ */

private data class OssLib(
    val name: String,
    val author: String,
    val license: String,
    val url: String
)

private const val APACHE = "Apache License 2.0"
private const val APACHE_URL = "https://www.apache.org/licenses/LICENSE-2.0"

// Libraries this app depends on. Add a line here when you add a dependency.
private val ossLibraries = listOf(
    OssLib("Jetpack Compose (UI, Foundation, Material 3, Icons)", "Google / AndroidX", APACHE, "https://developer.android.com/jetpack/compose"),
    OssLib("AndroidX Activity & Core KTX", "Google / AndroidX", APACHE, "https://developer.android.com/jetpack/androidx"),
    OssLib("AndroidX Lifecycle (ViewModel)", "Google / AndroidX", APACHE, "https://developer.android.com/jetpack/androidx/releases/lifecycle"),
    OssLib("AndroidX Credentials (Credential Manager)", "Google / AndroidX", APACHE, "https://developer.android.com/jetpack/androidx/releases/credentials"),
    OssLib("Google Identity Services (googleid)", "Google", APACHE, "https://developers.google.com/identity"),
    OssLib("Firebase Authentication", "Google / Firebase", APACHE, "https://firebase.google.com/docs/auth"),
    OssLib("Cloud Firestore", "Google / Firebase", APACHE, "https://firebase.google.com/docs/firestore"),
    OssLib("Kotlin & Kotlin Coroutines", "JetBrains", APACHE, "https://github.com/Kotlin/kotlinx.coroutines")
)

/** Drop this into SettingsView as the last card. */
@Composable
fun AboutCard() {
    val cs = MaterialTheme.colorScheme
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showLicenses by rememberSaveable { mutableStateOf(false) }

    SectionCard {
        Text("About", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        ListItem(
            modifier = Modifier.clip(MaterialTheme.shapes.large).clickable { showAbout = true },
            colors = ListItemDefaults.colors(containerColor = cs.surfaceContainerHigh),
            leadingContent = { Icon(Icons.Rounded.Info, null, tint = cs.primary) },
            headlineContent = { Text("About Self Attendance", fontWeight = FontWeight.Bold) },
            supportingContent = { Text("Version, features and privacy") },
            trailingContent = { Icon(Icons.Rounded.ChevronRight, null, tint = cs.onSurfaceVariant) }
        )
        Spacer(Modifier.height(8.dp))
        ListItem(
            modifier = Modifier.clip(MaterialTheme.shapes.large).clickable { showLicenses = true },
            colors = ListItemDefaults.colors(containerColor = cs.surfaceContainerHigh),
            leadingContent = { Icon(Icons.Rounded.Description, null, tint = cs.primary) },
            headlineContent = { Text("Open source licenses", fontWeight = FontWeight.Bold) },
            supportingContent = { Text("Libraries used in this app") },
            trailingContent = { Icon(Icons.Rounded.ChevronRight, null, tint = cs.onSurfaceVariant) }
        )
    }

    if (showAbout) AboutDialog(onDismiss = { showAbout = false }, onOpenLicenses = { showAbout = false; showLicenses = true })
    if (showLicenses) LicensesDialog(onDismiss = { showLicenses = false })
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit, onOpenLicenses: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val version = rememberAppVersion()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(shape = RoundedCornerShape(18.dp), color = cs.primaryContainer, modifier = Modifier.size(56.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.CalendarMonth, null, tint = cs.onPrimaryContainer, modifier = Modifier.size(30.dp))
                }
            }
        },
        title = { Text("Self Attendance", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Version $version", style = MaterialTheme.typography.labelLarge, color = cs.primary)
                Text(
                    "Track your day and night shifts, leaves and overtime on a simple calendar, and keep your own records in one place.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Your attendance is stored on this device and synced to your signed-in account so it is available after you sign in again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
                Text("© ${java.time.Year.now()} debaxyz", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = { TextButton(onClick = onOpenLicenses) { Text("Licenses") } }
    )
}

@Composable
private fun LicensesDialog(onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Description, null, tint = cs.primary) },
        title = { Text("Open source licenses", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.heightIn(max = 420.dp)) {
                item {
                    Text(
                        "Self Attendance is built with the open source libraries below. Thank you to their authors and contributors.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
                items(ossLibraries) { lib ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = cs.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { runCatching { uriHandler.openUri(lib.url) } }
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(lib.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(lib.author, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            Text(lib.license, style = MaterialTheme.typography.labelMedium, color = cs.primary, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                item {
                    TextButton(onClick = { runCatching { uriHandler.openUri(APACHE_URL) } }) {
                        Icon(Icons.Rounded.OpenInNew, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Read Apache 2.0 license text")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
