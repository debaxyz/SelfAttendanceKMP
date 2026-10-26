package com.debaxyz.selfattendance.platform

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore

/** Default Firestore database only on the web. */
actual fun createFirestore(): FirebaseFirestore = Firebase.firestore
