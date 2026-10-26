package com.debaxyz.selfattendance.platform

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.debaxyz.selfattendance.R
import com.debaxyz.selfattendance.data.repository.AndroidAttendanceRepository
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/** Holds the current Activity (needed to show the Google account sheet) without leaking it. */
object AndroidServices {
    private var activityRef: WeakReference<Activity>? = null
    private var cached: PlatformServices? = null

    fun attach(activity: Activity) { activityRef = WeakReference(activity) }
    fun detach(activity: Activity) { if (activityRef?.get() === activity) activityRef = null }

    fun services(appContext: Context): PlatformServices = cached ?: firebaseServices(
        google = AndroidGoogleSignIn(appContext.applicationContext) { activityRef?.get() },
        network = AndroidNetworkMonitor(appContext.applicationContext),
        repository = AndroidAttendanceRepository(appContext.applicationContext)
    ).also { cached = it }
}

class AndroidGoogleSignIn(
    private val appContext: Context,
    private val activity: () -> Activity?
) : GoogleIdTokenProvider {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun requestToken(silent: Boolean, callback: GoogleTokenCallback) {
        scope.launch {
            val result = try {
                fetch(silent)
            } catch (e: CancellationException) {
                throw e
            } catch (e: GetCredentialCancellationException) {
                GoogleTokenResult(cancelled = true)
            } catch (e: Exception) {
                GoogleTokenResult(error = e.message ?: "Google Sign-In failed")
            }
            callback.onResult(result)
        }
    }

    private suspend fun fetch(silent: Boolean): GoogleTokenResult {
        val act = activity()
            ?: return GoogleTokenResult(error = "Could not find Activity to display Google Sign-In UI")
        val clientId = try {
            appContext.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            return GoogleTokenResult(error = "Google Sign-In configuration missing: default_web_client_id not found")
        }
        val option = if (silent) {
            GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(true)
                .build()
        } else {
            GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        }
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(act).getCredential(act, request).credential
        return if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            GoogleTokenResult(idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken)
        } else {
            GoogleTokenResult(error = "Unexpected credential type returned")
        }
    }

    override fun clearState(callback: GoogleDoneCallback) {
        scope.launch {
            try {
                CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest())
            } catch (_: Exception) {
            }
            callback.onDone()
        }
    }
}

class AndroidNetworkMonitor(private val context: Context) : NetworkMonitor {
    private val cm get() = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    override fun isOnline(): Boolean {
        val cm = cm ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    override fun observe(): Flow<Boolean> = callbackFlow {
        val cm = cm
        trySend(isOnline())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(isOnline()) }
            override fun onLost(network: Network) { trySend(isOnline()) }
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(isOnline())
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            cm?.registerNetworkCallback(request, callback)
        } catch (_: Exception) {
            // Older devices / missing permission: fall back to the one-shot value.
        }
        awaitClose { try { cm?.unregisterNetworkCallback(callback) } catch (_: Exception) {} }
    }.distinctUntilChanged()
}
