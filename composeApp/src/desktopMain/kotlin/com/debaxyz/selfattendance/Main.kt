package com.debaxyz.selfattendance

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.debaxyz.selfattendance.data.auth.LocalAuthService
import com.debaxyz.selfattendance.data.repository.LocalAttendanceRepository
import com.debaxyz.selfattendance.platform.DesktopNetworkMonitor
import com.debaxyz.selfattendance.platform.PlatformServices
import com.debaxyz.selfattendance.platform.UnsupportedGoogleSignIn

/** Desktop runs in local-only mode: no Firebase, data is stored on this machine. */
fun main() = application {
    val services = remember {
        PlatformServices(
            google = UnsupportedGoogleSignIn("Google sign-in is not available on desktop."),
            network = DesktopNetworkMonitor(),
            auth = LocalAuthService(),
            repository = LocalAttendanceRepository(),
            cloudEnabled = false
        )
    }
    Window(onCloseRequest = ::exitApplication, title = "Self Attendance") {
        App(services)
    }
}
