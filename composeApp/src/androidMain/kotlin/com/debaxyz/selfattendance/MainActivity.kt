package com.debaxyz.selfattendance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.debaxyz.selfattendance.platform.AndroidServices

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AndroidServices.attach(this)
        setContent { App(AndroidServices.services(applicationContext)) }
    }

    override fun onDestroy() {
        AndroidServices.detach(this)
        super.onDestroy()
    }
}
