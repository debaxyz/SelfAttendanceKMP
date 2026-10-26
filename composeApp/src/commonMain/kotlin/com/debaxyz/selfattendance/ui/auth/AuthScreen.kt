package com.debaxyz.selfattendance.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.debaxyz.selfattendance.data.auth.AuthErrorKind
import com.debaxyz.selfattendance.data.auth.AuthException
import com.debaxyz.selfattendance.data.auth.AuthService
import com.debaxyz.selfattendance.platform.GoogleIdTokenProvider
import com.debaxyz.selfattendance.platform.LocalPlatformServices
import com.debaxyz.selfattendance.platform.clear
import com.debaxyz.selfattendance.platform.token
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dedicated application-lifetime coroutine scope for auth background operations
 * such as clearing credential state without being cancelled when UI composables unmount.
 */
private val authBackgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** Sign out of Firebase and clear the platform's Google account state in the background. */
fun signOut(
    google: GoogleIdTokenProvider,
    auth: AuthService,
    onSignOutComplete: () -> Unit = {}
) {
    authBackgroundScope.launch {
        try {
            auth.signOut()
        } catch (e: Exception) {
            if (e !is CancellationException) println("Auth: Firebase signOut error: ${e.message}")
        }
        try {
            google.clear()
        } catch (e: Exception) {
            if (e !is CancellationException) println("Auth: Could not clear Google state: ${e.message}")
        } finally {
            withContext(Dispatchers.Main) { onSignOutComplete() }
        }
    }
}

/**
 * Structured auth error representation with actionable guidance for the user.
 */
data class AuthErrorInfo(
    val message: String,
    val showGooglePrompt: Boolean = false,
    val showResetPrompt: Boolean = false,
    val showSignInToggle: Boolean = false
)

private fun Throwable.authKind(): AuthErrorKind? = (this as? AuthException)?.kind

fun parseAuthError(e: Throwable, isSignUp: Boolean): AuthErrorInfo {
    val msg = e.message.orEmpty()
    return when {
        e.authKind() == AuthErrorKind.USER_COLLISION || msg.contains("already in use", ignoreCase = true) -> {
            AuthErrorInfo(
                message = "This email is already registered. If you created this account with Google, tap 'Continue with Google' below, or switch to 'Sign in'.",
                showGooglePrompt = true,
                showSignInToggle = true,
                showResetPrompt = true
            )
        }
        e.authKind() == AuthErrorKind.INVALID_CREDENTIALS || msg.contains("credential is incorrect", ignoreCase = true) || msg.contains("malformed", ignoreCase = true) || msg.contains("expired", ignoreCase = true) -> {
            AuthErrorInfo(
                message = "The password entered is incorrect, or this account was registered with Google. You can sign in with Google or tap 'Forgot password?' to set a password.",
                showGooglePrompt = true,
                showResetPrompt = true
            )
        }
        e.authKind() == AuthErrorKind.INVALID_USER || msg.contains("no user record", ignoreCase = true) -> {
            AuthErrorInfo(
                message = "No account found with this email. Tap 'Create account' below to sign up, or sign in with Google.",
                showSignInToggle = true,
                showGooglePrompt = true
            )
        }
        msg.contains("weak", ignoreCase = true) -> {
            AuthErrorInfo(
                message = "Password is too weak. Please use at least 6 characters."
            )
        }
        msg.contains("network", ignoreCase = true) ||
            msg.contains("unreachable", ignoreCase = true) ||
            msg.contains("unable to resolve", ignoreCase = true) ||
            msg.contains("offline", ignoreCase = true) ||
            msg.contains("timeout", ignoreCase = true) -> {
            AuthErrorInfo(
                message = "No internet connection. Check your Wi‑Fi or mobile data and try again."
            )
        }
        else -> {
            AuthErrorInfo(
                message = e.message ?: "Authentication failed. Please verify your details or use Google Sign-In.",
                showGooglePrompt = true,
                showResetPrompt = true
            )
        }
    }
}

/**
 * Suspending email & password authentication helpers
 */
suspend fun signInWithEmail(auth: AuthService, email: String, pass: String): Result<Unit> = runCatching {
    auth.signInWithEmail(email.trim(), pass)
}

suspend fun signUpWithEmail(auth: AuthService, email: String, pass: String): Result<Unit> = runCatching {
    auth.signUpWithEmail(email.trim(), pass)
}

suspend fun sendPasswordReset(auth: AuthService, email: String): Result<Unit> = runCatching {
    auth.sendPasswordReset(email.trim())
}

/** Silent auto sign-in attempt on app launch. */
suspend fun attemptAutoSignIn(google: GoogleIdTokenProvider, auth: AuthService): Boolean {
    if (auth.currentUser != null) return true
    return try {
        val r = google.token(silent = true)
        val idToken = r.idToken ?: return false
        auth.signInWithGoogle(idToken, r.accessToken)
        true
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }
}

/** Interactive Sign-In with Google (account sheet). */
fun onGoogleSignInClicked(
    google: GoogleIdTokenProvider,
    auth: AuthService,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    scope.launch {
        try {
            val r = google.token(silent = false)
            when {
                r.cancelled -> onAuthCancelled()
                r.idToken != null -> {
                    auth.signInWithGoogle(r.idToken, r.accessToken)
                    onAuthSuccess()
                }
                else -> onAuthError(r.error ?: "Unexpected credential type returned")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("Auth: Google Sign-In failed: ${e.message}")
            onAuthError(parseAuthError(e, isSignUp = false).message)
        }
    }
}

@Composable
fun GoogleGIcon(modifier: Modifier = Modifier) {
    // Official multicolor Google "G" (48x48 viewport), drawn with vector paths.
    val parts = remember {
        listOf(
            "M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z" to Color(0xFFEA4335),
            "M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z" to Color(0xFF4285F4),
            "M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z" to Color(0xFFFBBC05),
            "M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z" to Color(0xFF34A853)
        ).map { (d, c) -> PathParser().parsePathString(d).toPath() to c }
    }
    Canvas(modifier = modifier.size(24.dp)) {
        scale(size.width / 48f, Offset.Zero) {
            parts.forEach { (path, color) -> drawPath(path, color) }
        }
    }
}

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    val services = LocalPlatformServices.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    // Live network status – updates when Wi‑Fi / mobile data goes on or off
    val isOnline by produceState(initialValue = services.network.isOnline()) {
        services.network.observe().collect { value = it }
    }

    // Pre-fill user email for smooth mobile testing
    var emailInput by rememberSaveable { mutableStateOf("") }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isSignUp by rememberSaveable { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<AuthErrorInfo?>(null) }
    var successNotification by remember { mutableStateOf<String?>(null) }
    var showForgotDialog by remember { mutableStateOf(false) }

    // Dynamic theme: every colour comes from the active MaterialTheme scheme
    // (Material You on Android 12+, light/dark aware), nothing hardcoded.
    val cs = MaterialTheme.colorScheme
    val bgColor = cs.background
    val onBg = cs.onBackground
    val cardColor = cs.surfaceContainerHigh
    val borderColor = cs.outlineVariant
    val brandContainer = cs.primaryContainer
    val onBrandContainer = cs.onPrimaryContainer
    val accent = cs.primary
    val buttonColor = cs.primary
    val onButton = cs.onPrimary
    val textMuted = cs.onSurfaceVariant
    val successContainer = cs.tertiaryContainer
    val onSuccessContainer = cs.onTertiaryContainer

    fun requireOnlineOrFail(): Boolean {
        if (services.network.isOnline()) return true
        authError = AuthErrorInfo(
            message = "No internet connection. Check your Wi‑Fi or mobile data and try again."
        )
        isLoading = false
        return false
    }

    fun triggerGoogleSignIn() {
        focusManager.clearFocus()
        authError = null
        successNotification = null
        if (!requireOnlineOrFail()) return
        isLoading = true
        onGoogleSignInClicked(
            google = services.google,
            auth = services.auth,
            onAuthSuccess = {
                isLoading = false
                onAuthSuccess()
            },
            onAuthError = { err ->
                isLoading = false
                // Prefer a clear offline message when the device is offline
                authError = if (!services.network.isOnline()) {
                    AuthErrorInfo("No internet connection. Check your Wi‑Fi or mobile data and try again.")
                } else {
                    AuthErrorInfo(err, showGooglePrompt = false, showResetPrompt = true)
                }
            },
            scope = coroutineScope,
            onAuthCancelled = {
                isLoading = false
            }
        )
    }

    // Attempt silent auto sign-in once if Google account is already authorized
    LaunchedEffect(Unit) {
        if (services.auth.currentUser == null) {
            val autoSuccess = attemptAutoSignIn(services.google, services.auth)
            if (autoSuccess) {
                onAuthSuccess()
            }
        }
    }

    Scaffold(
        containerColor = bgColor
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(bgColor)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top
        ) {
            // 1. Top Bar: Calendar Icon + "SELF ATTENDANCE" + "Your shifts. Your records."
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = brandContainer,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.CalendarMonth,
                            contentDescription = null,
                            tint = onBrandContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Column {
                    Text(
                        "SELF ATTENDANCE",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        "Your shifts. Your records.",
                        style = MaterialTheme.typography.bodySmall,
                        color = textMuted
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Offline banner – shown while device has no validated internet
            if (!isOnline) {
                Surface(
                    color = cs.errorContainer,
                    contentColor = cs.onErrorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("offline_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.WifiOff, contentDescription = null)
                        Column(Modifier.weight(1f)) {
                            Text(
                                "No internet connection",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Sign-in needs Wi‑Fi or mobile data. Reconnect and try again.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 2. Large Padlock Icon Squircle
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = brandContainer,
                modifier = Modifier.size(76.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = "Security lock",
                        tint = onBrandContainer,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Spacer(Modifier.height(26.dp))

            // 3. Welcome back / Create account Headline & Subtitle
            Text(
                if (isSignUp) "Create account" else "Welcome back",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = onBg
            )

            Spacer(Modifier.height(8.dp))

            Text(
                if (isSignUp)
                    "Sign up to access your attendance calendar and shift history."
                else
                    "Sign in to access your attendance calendar and shift history.",
                style = MaterialTheme.typography.bodyLarge,
                color = textMuted
            )

            Spacer(Modifier.height(26.dp))

            // Success feedback notification if any
            if (successNotification != null) {
                Surface(
                    color = successContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = onSuccessContainer
                        )
                        Text(
                            successNotification ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = onSuccessContainer
                        )
                    }
                }
            }

            // Enhanced Error display with direct solution actions
            if (authError != null) {
                val err = authError!!
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Text(
                                err.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }

                        // Helpful action chips/buttons inside the error banner
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (err.showGooglePrompt) {
                                Button(
                                    onClick = { triggerGoogleSignIn() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = Color(0xFF1E3A8A)
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    GoogleGIcon(Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Use Google", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (err.showSignInToggle) {
                                OutlinedButton(
                                    onClick = {
                                        isSignUp = false
                                        authError = null
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(36.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)
                                ) {
                                    Text("Sign In mode", style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            if (err.showResetPrompt) {
                                TextButton(
                                    onClick = { showForgotDialog = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text(
                                        "Set/Reset Password",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Email address input field
            OutlinedTextField(
                value = emailInput,
                onValueChange = {
                    emailInput = it
                    authError = null
                    successNotification = null
                },
                placeholder = {
                    Text("Email address", color = textMuted)
                },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Mail,
                        contentDescription = "Email",
                        tint = textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = cardColor,
                    unfocusedContainerColor = cardColor,
                    focusedBorderColor = accent,
                    cursorColor = accent,
                    unfocusedBorderColor = borderColor,
                    focusedTextColor = onBg,
                    unfocusedTextColor = onBg
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("email_input")
            )

            Spacer(Modifier.height(14.dp))

            // 5. Password input field
            OutlinedTextField(
                value = passwordInput,
                onValueChange = {
                    passwordInput = it
                    authError = null
                    successNotification = null
                },
                placeholder = {
                    Text("Password", color = textMuted)
                },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = "Password",
                        tint = textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = textMuted
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = cardColor,
                    unfocusedContainerColor = cardColor,
                    focusedBorderColor = accent,
                    cursorColor = accent,
                    unfocusedBorderColor = borderColor,
                    focusedTextColor = onBg,
                    unfocusedTextColor = onBg
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("password_input")
            )

            // 6. "Forgot password?" Link
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    "Forgot password?",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = accent,
                    modifier = Modifier
                        .clickable {
                            authError = null
                            showForgotDialog = true
                        }
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                        .testTag("forgot_password_button")
                )
            }

            Spacer(Modifier.height(20.dp))

            // 7. Main "Sign in ->" or "Create account ->" Button (Email & Password Authentication)
            Button(
                onClick = {
                    focusManager.clearFocus()
                    val email = emailInput.trim()
                    val pass = passwordInput
                    if (email.isBlank()) {
                        authError = AuthErrorInfo("Please enter your email address.")
                        return@Button
                    }
                    if (!email.contains("@") || !email.contains(".")) {
                        authError = AuthErrorInfo("Please enter a valid email address.")
                        return@Button
                    }
                    if (pass.isBlank()) {
                        authError = AuthErrorInfo("Please enter your password.")
                        return@Button
                    }
                    if (isSignUp && pass.length < 6) {
                        authError = AuthErrorInfo("Password must be at least 6 characters.")
                        return@Button
                    }
                    // Block email/password auth when offline
                    if (!requireOnlineOrFail()) return@Button

                    isLoading = true
                    authError = null
                    successNotification = null

                    coroutineScope.launch {
                        try {
                            if (isSignUp) {
                                val result = signUpWithEmail(services.auth, email, pass)
                                result.onSuccess {
                                    isLoading = false
                                    onAuthSuccess()
                                }.onFailure { e ->
                                    isLoading = false
                                    authError = parseAuthError(e, isSignUp = true)
                                }
                            } else {
                                val result = signInWithEmail(services.auth, email, pass)
                                result.onSuccess {
                                    isLoading = false
                                    onAuthSuccess()
                                }.onFailure { e ->
                                    isLoading = false
                                    authError = parseAuthError(e, isSignUp = false)
                                }
                            }
                        } catch (e: Exception) {
                            if (e !is CancellationException) {
                                isLoading = false
                                authError = parseAuthError(e, isSignUp)
                            }
                        }
                    }
                },
                enabled = !isLoading,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor,
                    contentColor = onButton
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("sign_in_submit_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = onButton,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (isSignUp) "Creating account..." else "Signing in...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = onButton
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            if (isSignUp) "Create account" else "Sign in",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = onButton
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = onButton,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // "OR" divider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f).height(1.dp).background(borderColor))
                Text(
                    "OR",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Box(Modifier.weight(1f).height(1.dp).background(borderColor))
            }

            Spacer(Modifier.height(16.dp))

            // 8. Google Sign-In button (round, icon only, via Credential Manager)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                OutlinedButton(
                    onClick = { triggerGoogleSignIn() },
                    enabled = !isLoading,
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFFFFFF),
                        contentColor = onBg
                    ),
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier
                        .size(56.dp)
                        .semantics { contentDescription = "Continue with Google" }
                        .testTag("continue_with_google_button")
                ) {
                    GoogleGIcon()
                }
            }

            Spacer(Modifier.height(32.dp))

            // 9. Bottom Link: "New here? Create account" / "Already have an account? Sign in"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (isSignUp) "Already have an account? " else "New here? ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textMuted
                )
                Text(
                    if (isSignUp) "Sign in" else "Create account",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    modifier = Modifier
                        .clickable {
                            isSignUp = !isSignUp
                            authError = null
                            successNotification = null
                        }
                        .padding(4.dp)
                        .testTag("toggle_sign_up_mode")
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showForgotDialog) {
        var resetEmail by rememberSaveable { mutableStateOf(emailInput.trim()) }
        var isResetting by remember { mutableStateOf(false) }
        var resetError by remember { mutableStateOf<String?>(null) }
        var resetSent by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                if (!isResetting) showForgotDialog = false
            },
            icon = { Icon(Icons.Rounded.LockReset, contentDescription = null, tint = accent) },
            title = { Text("Reset or Set Password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (resetSent) {
                        Text(
                            "A password setup link was sent to $resetEmail. Open the link in your email to set a custom password, which will allow you to sign in with both password and Google.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = onBg
                        )
                    } else {
                        Text(
                            "Enter your email to receive a password reset link. You can use this to set a password even if you originally registered with Google.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedTextField(
                            value = resetEmail,
                            onValueChange = {
                                resetEmail = it
                                resetError = null
                            },
                            placeholder = { Text("Email address") },
                            leadingIcon = {
                                Icon(Icons.Rounded.Mail, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (resetError != null) {
                            Text(
                                resetError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (resetSent) {
                    TextButton(onClick = {
                        showForgotDialog = false
                        successNotification = "Password link sent to $resetEmail"
                    }) {
                        Text("Done")
                    }
                } else {
                    Button(
                        onClick = {
                            val targetEmail = resetEmail.trim()
                            if (targetEmail.isBlank() || !targetEmail.contains("@")) {
                                resetError = "Please enter a valid email address."
                                return@Button
                            }
                            if (!services.network.isOnline()) {
                                resetError = "No internet connection. Check your Wi‑Fi or mobile data and try again."
                                return@Button
                            }
                            isResetting = true
                            resetError = null
                            coroutineScope.launch {
                                try {
                                    val res = sendPasswordReset(services.auth, targetEmail)
                                    res.onSuccess {
                                        isResetting = false
                                        resetSent = true
                                    }.onFailure { err ->
                                        isResetting = false
                                        resetError = parseAuthError(err, false).message
                                    }
                                } catch (e: Exception) {
                                    if (e !is CancellationException) {
                                        isResetting = false
                                        // `localizedMessage` is JVM-only; use common `message`.
                                        resetError = e.message ?: "Failed to send reset link."
                                    }
                                }
                            }
                        },
                        enabled = !isResetting,
                        colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = onButton)
                    ) {
                        if (isResetting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = onButton)
                            Spacer(Modifier.width(8.dp))
                            Text("Sending...")
                        } else {
                            Text("Send Link")
                        }
                    }
                }
            },
            dismissButton = {
                if (!resetSent) {
                    TextButton(
                        onClick = { showForgotDialog = false },
                        enabled = !isResetting
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}