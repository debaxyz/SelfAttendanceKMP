package com.debaxyz.selfattendance.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.debaxyz.selfattendance.data.auth.AuthService
import com.debaxyz.selfattendance.data.repository.AttendanceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/* ---------- Google sign-in (Credential Manager on Android, GoogleSignIn SDK on iOS) ---------- */

/** Plain class (not a sealed type) so it is easy to build from Swift. */
class GoogleTokenResult(
    val idToken: String? = null,
    val accessToken: String? = null,
    val cancelled: Boolean = false,
    val error: String? = null
)

interface GoogleTokenCallback { fun onResult(result: GoogleTokenResult) }
interface GoogleDoneCallback { fun onDone() }

interface GoogleIdTokenProvider {
    /** silent = true: only an already-authorised account, never shows UI. silent = false: shows the account sheet. */
    fun requestToken(silent: Boolean, callback: GoogleTokenCallback)
    /** Forget the cached Google account (called on sign-out / account deletion). */
    fun clearState(callback: GoogleDoneCallback)
}

suspend fun GoogleIdTokenProvider.token(silent: Boolean): GoogleTokenResult =
    suspendCancellableCoroutine { cont ->
        requestToken(silent, object : GoogleTokenCallback {
            override fun onResult(result: GoogleTokenResult) { if (cont.isActive) cont.resume(result) }
        })
    }

suspend fun GoogleIdTokenProvider.clear() =
    suspendCancellableCoroutine<Unit> { cont ->
        clearState(object : GoogleDoneCallback {
            override fun onDone() { if (cont.isActive) cont.resume(Unit) }
        })
    }

class GoogleSignInCancelledException : Exception("Google sign-in was cancelled")

/* ---------- Connectivity ---------- */

interface NetworkMonitor {
    fun isOnline(): Boolean
    /** Emits the current state immediately, then on every change. */
    fun observe(): Flow<Boolean>
}

/* ---------- Services handed to the common UI ---------- */

class PlatformServices(
    val google: GoogleIdTokenProvider,
    val network: NetworkMonitor,
    val auth: AuthService,
    val repository: AttendanceRepository,
    /** false = local-only mode (Desktop, Wasm): no sign-in, data stays on this device. */
    val cloudEnabled: Boolean
)

/** For platforms without a Google sign-in flow (Desktop, Web). */
class UnsupportedGoogleSignIn(private val message: String) : GoogleIdTokenProvider {
    override fun requestToken(silent: Boolean, callback: GoogleTokenCallback) =
        callback.onResult(GoogleTokenResult(error = message))
    override fun clearState(callback: GoogleDoneCallback) = callback.onDone()
}

val LocalPlatformServices = staticCompositionLocalOf<PlatformServices> {
    error("PlatformServices not provided")
}

/* ---------- File export / import ---------- */

class FileActions(
    val saveCsv: (Triple<String, String, String>) -> Unit,   // (file name, content, toast message)
    val saveJson: (Triple<String, String, String>) -> Unit,
    val restore: () -> Unit
)

@Composable
expect fun rememberFileActions(onToast: (String) -> Unit, onRestoreText: (String) -> Unit): FileActions

/* ---------- Small platform hooks ---------- */

@Composable expect fun platformColorScheme(dark: Boolean): ColorScheme
@Composable expect fun SystemBarsEffect(dark: Boolean)
@Composable expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
@Composable expect fun rememberAppVersion(): String

/** Locale-aware thousands grouping, e.g. 12,34,567 (en-IN) or 1,234,567 (en-US). */
expect fun formatGrouped(n: Long): String
