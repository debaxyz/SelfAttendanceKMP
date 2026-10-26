package com.debaxyz.selfattendance.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore
import java.text.NumberFormat

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

actual fun formatGrouped(n: Long): String = NumberFormat.getInstance().format(n)

@Composable
actual fun platformColorScheme(dark: Boolean): ColorScheme {
    val ctx = LocalContext.current
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
}

@Composable
actual fun SystemBarsEffect(dark: Boolean) {
    val view = LocalView.current
    SideEffect {
        view.context.findActivity()?.window?.let { w ->
            WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}

@Composable
actual fun rememberAppVersion(): String {
    val ctx = LocalContext.current
    return remember {
        try {
            val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode
            else @Suppress("DEPRECATION") pi.versionCode.toLong()
            "${pi.versionName ?: "1.0"} ($code)"
        } catch (_: Exception) {
            "1.0"
        }
    }
}

private class PendingWrite {
    var content: String? = null
    var msg: String? = null
}

@Composable
actual fun rememberFileActions(onToast: (String) -> Unit, onRestoreText: (String) -> Unit): FileActions {
    val ctx = LocalContext.current
    val pending = remember { PendingWrite() }

    fun writeUri(uri: Uri?) {
        val content = pending.content
        if (uri != null && content != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                onToast(pending.msg ?: "Saved")
            } catch (_: Exception) {
                onToast("Could not save file.")
            }
        }
        pending.content = null
        pending.msg = null
    }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { writeUri(it) }
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { writeUri(it) }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                onRestoreText(text)
            } catch (_: Exception) {
                onToast("Error reading backup file.")
            }
        }
    }

    return remember(csvLauncher, jsonLauncher, openLauncher) {
        FileActions(
            saveCsv = { (name, content, msg) -> pending.content = content; pending.msg = msg; csvLauncher.launch(name) },
            saveJson = { (name, content, msg) -> pending.content = content; pending.msg = msg; jsonLauncher.launch(name) },
            restore = { openLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
        )
    }
}

/** Unused on Android (see AndroidAttendanceRepository); kept to satisfy the shared expect declaration. */
actual fun createFirestore(): FirebaseFirestore = Firebase.firestore
