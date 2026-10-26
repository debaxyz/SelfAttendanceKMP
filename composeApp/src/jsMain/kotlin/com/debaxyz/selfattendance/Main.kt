package com.debaxyz.selfattendance

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.debaxyz.selfattendance.platform.UnsupportedGoogleSignIn
import com.debaxyz.selfattendance.platform.WebNetworkMonitor
import com.debaxyz.selfattendance.platform.firebaseServices
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.initialize
import kotlinx.browser.document

/** Web (JS) build: Firebase cloud sync, email + password sign-in. */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    Firebase.initialize(
        null,
        FirebaseOptions(
            applicationId = WebFirebaseConfig.APP_ID,
            apiKey = WebFirebaseConfig.API_KEY,
            projectId = WebFirebaseConfig.PROJECT_ID,
            authDomain = WebFirebaseConfig.AUTH_DOMAIN,
            storageBucket = WebFirebaseConfig.STORAGE_BUCKET
        )
    )
    val services = firebaseServices(
        google = UnsupportedGoogleSignIn("Google sign-in is not available in the web build yet. Please use email and password."),
        network = WebNetworkMonitor()
    )
    ComposeViewport(document.body!!) { App(services) }
}
