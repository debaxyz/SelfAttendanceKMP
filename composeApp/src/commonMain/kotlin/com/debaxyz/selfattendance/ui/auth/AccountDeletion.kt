package com.debaxyz.selfattendance.ui.auth

import com.debaxyz.selfattendance.data.auth.AuthErrorKind
import com.debaxyz.selfattendance.data.auth.AuthException
import com.debaxyz.selfattendance.data.auth.AuthService
import com.debaxyz.selfattendance.platform.GoogleIdTokenProvider
import com.debaxyz.selfattendance.platform.GoogleSignInCancelledException
import com.debaxyz.selfattendance.platform.token
import kotlinx.coroutines.CancellationException

/* ============================================================
 *  ACCOUNT DELETION HELPERS
 *  Firebase requires a recent sign-in before deleting an account,
 *  so we always re-verify the user first.
 * ============================================================ */

/** Re-verify identity with Google (shows the Google account sheet). */
suspend fun reauthenticateWithGoogle(google: GoogleIdTokenProvider, auth: AuthService): Result<Unit> = try {
    val r = google.token(silent = false)
    when {
        r.cancelled -> Result.failure(GoogleSignInCancelledException())
        r.idToken != null -> {
            auth.reauthenticateWithGoogle(r.idToken, r.accessToken)
            Result.success(Unit)
        }
        else -> Result.failure(IllegalStateException(r.error ?: "Unexpected credential type returned"))
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

/** Re-verify identity with the account password (email/password users). */
suspend fun reauthenticateWithPassword(auth: AuthService, password: String): Result<Unit> = try {
    auth.reauthenticateWithPassword(password)
    Result.success(Unit)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

fun deleteAccountErrorMessage(e: Throwable): String {
    val msg = e.message.orEmpty()
    val kind = (e as? AuthException)?.kind
    return when {
        e is GoogleSignInCancelledException -> "Verification was cancelled. Nothing was deleted."
        kind == AuthErrorKind.INVALID_CREDENTIALS -> "Incorrect password. Nothing was deleted."
        kind == AuthErrorKind.INVALID_USER || msg.contains("mismatch", ignoreCase = true) ->
            "Please verify with the same account you are signed in with."
        kind == AuthErrorKind.RECENT_LOGIN_REQUIRED -> "Please try again to confirm it's you."
        msg.contains("network", ignoreCase = true) ||
            msg.contains("unavailable", ignoreCase = true) ||
            msg.contains("offline", ignoreCase = true) ->
            "No internet connection. Connect and try again. Nothing was deleted."
        else -> e.message ?: "Could not delete the account. Please try again."
    }
}
