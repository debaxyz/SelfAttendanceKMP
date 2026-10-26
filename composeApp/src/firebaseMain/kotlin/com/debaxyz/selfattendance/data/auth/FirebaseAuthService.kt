package com.debaxyz.selfattendance.data.auth

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.EmailAuthProvider
import dev.gitlive.firebase.auth.FirebaseAuthInvalidCredentialsException
import dev.gitlive.firebase.auth.FirebaseAuthInvalidUserException
import dev.gitlive.firebase.auth.FirebaseAuthRecentLoginRequiredException
import dev.gitlive.firebase.auth.FirebaseAuthUserCollisionException
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** Firebase Auth through GitLive: Android, iOS and JS. */
class FirebaseAuthService : AuthService {
    private val auth get() = Firebase.auth

    private fun FirebaseUser.toApp() = AppUser(
        uid = uid,
        email = email,
        displayName = displayName,
        usesGoogle = providerData.any { it.providerId == "google.com" }
    )

    override val currentUser: AppUser? get() = auth.currentUser?.toApp()

    override val authState: Flow<AppUser?> = flow { emitAll(auth.authStateChanged.map { it?.toApp() }) }

    private fun Throwable.toAuth(): AuthException = when (this) {
        is AuthException -> this
        is FirebaseAuthUserCollisionException -> AuthException(AuthErrorKind.USER_COLLISION, message, this)
        is FirebaseAuthInvalidCredentialsException -> AuthException(AuthErrorKind.INVALID_CREDENTIALS, message, this)
        is FirebaseAuthInvalidUserException -> AuthException(AuthErrorKind.INVALID_USER, message, this)
        is FirebaseAuthRecentLoginRequiredException -> AuthException(AuthErrorKind.RECENT_LOGIN_REQUIRED, message, this)
        else -> AuthException(AuthErrorKind.OTHER, message, this)
    }

    private suspend inline fun <T> mapAuth(block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw e.toAuth()
    }

    private fun requireUser(): FirebaseUser =
        auth.currentUser ?: throw AuthException(AuthErrorKind.OTHER, "You are not signed in.")

    override suspend fun signInWithEmail(email: String, password: String) = mapAuth {
        auth.signInWithEmailAndPassword(email, password); Unit
    }

    override suspend fun signUpWithEmail(email: String, password: String) = mapAuth {
        auth.createUserWithEmailAndPassword(email, password); Unit
    }

    override suspend fun sendPasswordReset(email: String) = mapAuth { auth.sendPasswordResetEmail(email) }

    override suspend fun signInWithGoogle(idToken: String, accessToken: String?) = mapAuth {
        auth.signInWithCredential(GoogleAuthProvider.credential(idToken, accessToken)); Unit
    }

    override suspend fun reauthenticateWithPassword(password: String) = mapAuth {
        val user = requireUser()
        val email = user.email ?: throw AuthException(AuthErrorKind.OTHER, "This account has no email address.")
        user.reauthenticate(EmailAuthProvider.credential(email, password)); Unit
    }

    override suspend fun reauthenticateWithGoogle(idToken: String, accessToken: String?) = mapAuth {
        requireUser().reauthenticate(GoogleAuthProvider.credential(idToken, accessToken)); Unit
    }

    override suspend fun deleteCurrentUser() = mapAuth { requireUser().delete() }

    override suspend fun signOut() = mapAuth { auth.signOut() }
}
