package com.debaxyz.selfattendance.data.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

data class AppUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val usesGoogle: Boolean
)

enum class AuthErrorKind { INVALID_CREDENTIALS, INVALID_USER, USER_COLLISION, RECENT_LOGIN_REQUIRED, OTHER }

class AuthException(val kind: AuthErrorKind, message: String?, cause: Throwable? = null) : Exception(message, cause)

/** Cloud backend for sign-in. Firebase implementation lives in firebaseMain (Android, iOS, JS). */
interface AuthService {
    val currentUser: AppUser?
    val authState: Flow<AppUser?>

    suspend fun signInWithEmail(email: String, password: String)
    suspend fun signUpWithEmail(email: String, password: String)
    suspend fun sendPasswordReset(email: String)
    suspend fun signInWithGoogle(idToken: String, accessToken: String?)
    suspend fun reauthenticateWithPassword(password: String)
    suspend fun reauthenticateWithGoogle(idToken: String, accessToken: String?)
    suspend fun deleteCurrentUser()
    suspend fun signOut()
}

/** Desktop and Wasm: no cloud, a single always-signed-in local user. */
class LocalAuthService : AuthService {
    private val user = AppUser(uid = "local", email = null, displayName = "This device", usesGoogle = false)
    override val currentUser: AppUser? = user
    override val authState: Flow<AppUser?> = MutableStateFlow(user)

    private fun unsupported(): Nothing =
        throw AuthException(AuthErrorKind.OTHER, "Sign-in is not available in local mode.")

    override suspend fun signInWithEmail(email: String, password: String) = unsupported()
    override suspend fun signUpWithEmail(email: String, password: String) = unsupported()
    override suspend fun sendPasswordReset(email: String) = unsupported()
    override suspend fun signInWithGoogle(idToken: String, accessToken: String?) = unsupported()
    override suspend fun reauthenticateWithPassword(password: String) = unsupported()
    override suspend fun reauthenticateWithGoogle(idToken: String, accessToken: String?) = unsupported()
    override suspend fun deleteCurrentUser() {}
    override suspend fun signOut() {}
}
