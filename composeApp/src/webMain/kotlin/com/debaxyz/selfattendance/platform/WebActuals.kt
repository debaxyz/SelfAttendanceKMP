package com.debaxyz.selfattendance.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.files.FileReader
import kotlin.math.abs

/** Groups digits by thousands with ',' (no java.text on the web). */
actual fun formatGrouped(n: Long): String {
    val s = abs(n).toString()
    val sb = StringBuilder()
    s.forEachIndexed { i, c ->
        if (i > 0 && (s.length - i) % 3 == 0) sb.append(',')
        sb.append(c)
    }
    return (if (n < 0) "-" else "") + sb
}

@Composable
actual fun platformColorScheme(dark: Boolean): ColorScheme =
    if (dark) darkColorScheme() else lightColorScheme()

@Composable actual fun SystemBarsEffect(dark: Boolean) {}

@Composable actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {}

@Composable actual fun rememberAppVersion(): String = "1.0 (web)"

class WebNetworkMonitor : NetworkMonitor {
    override fun isOnline(): Boolean = window.navigator.onLine

    override fun observe(): Flow<Boolean> = callbackFlow {
        trySend(isOnline())
        val on: (Event) -> Unit = { trySend(true); Unit }
        val off: (Event) -> Unit = { trySend(false); Unit }
        window.addEventListener("online", on)
        window.addEventListener("offline", off)
        awaitClose {
            window.removeEventListener("online", on)
            window.removeEventListener("offline", off)
        }
    }.distinctUntilChanged()
}

/** UTF-8 percent-encoding for a data: URL (encodeURIComponent is not available in Kotlin/Wasm). */
private fun percentEncode(text: String): String {
    val sb = StringBuilder()
    for (b in text.encodeToByteArray()) {
        val v = b.toInt() and 0xFF
        val c = v.toChar()
        if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '-' || c == '_' || c == '.' || c == '~') {
            sb.append(c)
        } else {
            sb.append('%')
            sb.append("0123456789ABCDEF"[v shr 4])
            sb.append("0123456789ABCDEF"[v and 0x0F])
        }
    }
    return sb.toString()
}

private fun download(name: String, mime: String, content: String) {
    val a = document.createElement("a") as HTMLAnchorElement
    a.href = "data:$mime;charset=utf-8,${percentEncode(content)}"
    a.download = name
    document.body?.appendChild(a)
    a.click()
    document.body?.removeChild(a)
}

private fun pickTextFile(onText: (String?) -> Unit) {
    val input = document.createElement("input") as HTMLInputElement
    input.type = "file"
    input.accept = ".json,.txt,application/json,text/plain"
    input.onchange = {
        val file = input.files?.item(0)
        if (file == null) {
            onText(null)
        } else {
            val reader = FileReader()
            reader.onload = { onText(reader.result?.toString()); null }
            reader.onerror = { onText(null); null }
            reader.readAsText(file)
        }
        null
    }
    input.click()
}

@Composable
actual fun rememberFileActions(onToast: (String) -> Unit, onRestoreText: (String) -> Unit): FileActions {
    val toast by rememberUpdatedState(onToast)
    val restoreText by rememberUpdatedState(onRestoreText)
    return remember {
        FileActions(
            saveCsv = { (name, content, msg) -> download(name, "text/csv", content); toast(msg) },
            saveJson = { (name, content, msg) -> download(name, "application/json", content); toast(msg) },
            restore = {
                pickTextFile { text ->
                    if (text != null) restoreText(text) else toast("Error reading backup file.")
                }
            }
        )
    }
}
