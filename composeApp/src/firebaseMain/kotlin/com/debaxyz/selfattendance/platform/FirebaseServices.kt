package com.debaxyz.selfattendance.platform

import com.debaxyz.selfattendance.data.auth.FirebaseAuthService
import com.debaxyz.selfattendance.data.repository.AttendanceRepository
import com.debaxyz.selfattendance.data.repository.FirebaseAttendanceRepository
import dev.gitlive.firebase.firestore.FirebaseFirestore

/** Default database (iOS, JS). Android bypasses this: its repository uses the native SDK to open the named database. */
expect fun createFirestore(): FirebaseFirestore

/** Cloud-backed services for Android, iOS and JS. Firebase must already be initialised. */
fun firebaseServices(
    google: GoogleIdTokenProvider,
    network: NetworkMonitor,
    repository: AttendanceRepository = FirebaseAttendanceRepository(createFirestore())
) = PlatformServices(
    google = google,
    network = network,
    auth = FirebaseAuthService(),
    repository = repository,
    cloudEnabled = true
)
