package com.debaxyz.selfattendance

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.debaxyz.selfattendance.platform.GoogleIdTokenProvider
import com.debaxyz.selfattendance.platform.IosNetworkMonitor
import com.debaxyz.selfattendance.platform.firebaseServices
import platform.UIKit.UIViewController

/** Called from Swift (ContentView.swift). `google` is implemented in Swift (GoogleSignInBridge). */
fun MainViewController(google: GoogleIdTokenProvider): UIViewController =
    ComposeUIViewController {
        val services = remember { firebaseServices(google, IosNetworkMonitor()) }
        App(services)
    }
