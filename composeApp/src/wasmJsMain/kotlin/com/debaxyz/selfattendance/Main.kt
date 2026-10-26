package com.debaxyz.selfattendance

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.debaxyz.selfattendance.data.auth.LocalAuthService
import com.debaxyz.selfattendance.data.repository.LocalAttendanceRepository
import com.debaxyz.selfattendance.platform.PlatformServices
import com.debaxyz.selfattendance.platform.UnsupportedGoogleSignIn
import com.debaxyz.selfattendance.platform.WebNetworkMonitor
import kotlinx.browser.document

/** Wasm build: local-only mode (data in this browser's localStorage, no Firebase). */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val services = PlatformServices(
        google = UnsupportedGoogleSignIn("Google sign-in is not available in local mode."),
        network = WebNetworkMonitor(),
        auth = LocalAuthService(),
        repository = LocalAttendanceRepository(),
        cloudEnabled = false
    )
    ComposeViewport(document.body!!) { App(services) }
}
