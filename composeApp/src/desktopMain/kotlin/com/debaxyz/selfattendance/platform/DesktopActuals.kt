package com.debaxyz.selfattendance.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.text.NumberFormat

actual fun formatGrouped(n: Long): String = NumberFormat.getInstance().format(n)

@Composable
actual fun platformColorScheme(dark: Boolean): ColorScheme =
    if (dark) darkColorScheme() else lightColorScheme()

@Composable actual fun SystemBarsEffect(dark: Boolean) {}

@Composable actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {}

@Composable actual fun rememberAppVersion(): String = "1.0 (desktop)"

class DesktopNetworkMonitor : NetworkMonitor {
    override fun isOnline(): Boolean = true
    override fun observe(): Flow<Boolean> = flowOf(true)
}

@Composable
actual fun rememberFileActions(onToast: (String) -> Unit, onRestoreText: (String) -> Unit): FileActions {
    val toast by rememberUpdatedState(onToast)
    val restoreText by rememberUpdatedState(onRestoreText)

    fun save(name: String, content: String, msg: String) {
        val dialog = FileDialog(null as Frame?, "Save", FileDialog.SAVE).apply { file = name; isVisible = true }
        val f = dialog.file ?: return
        try {
            File(dialog.directory, f).writeText(content, Charsets.UTF_8)
            toast(msg)
        } catch (_: Exception) {
            toast("Could not save file.")
        }
    }

    return remember {
        FileActions(
            saveCsv = { (name, content, msg) -> save(name, content, msg) },
            saveJson = { (name, content, msg) -> save(name, content, msg) },
            restore = {
                val dialog = FileDialog(null as Frame?, "Open backup", FileDialog.LOAD).apply { isVisible = true }
                val f = dialog.file
                if (f != null) {
                    try { restoreText(File(dialog.directory, f).readText(Charsets.UTF_8)) }
                    catch (_: Exception) { toast("Error reading backup file.") }
                }
            }
        )
    }
}
