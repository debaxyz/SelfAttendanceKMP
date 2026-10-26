package com.debaxyz.selfattendance.data.repository

import android.content.Context
import com.debaxyz.selfattendance.R
import com.debaxyz.selfattendance.data.model.Cfg
import com.debaxyz.selfattendance.data.model.Rec
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/** Android repository on the native Firebase SDK: GitLive cannot open a named Firestore database. */
class AndroidAttendanceRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : AttendanceRepository {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        FirebaseAuth.getInstance()
    )

    private fun requireUserId(expectedUserId: String? = null): String {
        val currentUid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before accessing Firestore.")
        if (expectedUserId != null && currentUid != expectedUserId) {
            throw IllegalStateException("User session mismatch: $currentUid != $expectedUserId")
        }
        return currentUid
    }

    override fun observeRecords(userId: String): Flow<CloudSnap<Map<String, Rec>>> {
        val path = "users/$userId/records"
        return db.collection("users").document(userId).collection("records")
            .snapshotFlow()
            .map { snapshot ->
                val result = LinkedHashMap<String, Rec>()
                for (doc in snapshot.documents) {
                    val date = doc.id
                    val s = doc.getString("s") ?: "p"
                    val sh = doc.getString("sh")
                    val ot = doc.getDouble("ot")
                    val inT = doc.getLong("inT")
                    val outT = doc.getLong("outT")
                    result[date] = Rec(
                        date = date,
                        s = s,
                        sh = sh,
                        ot = ot,
                        inT = inT,
                        outT = outT,
                        userId = userId
                    )
                }
                CloudSnap<Map<String, Rec>>(result, snapshot.metadata.isFromCache)
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error.toCloud()
            }
    }

    override suspend fun saveRecord(userId: String, rec: Rec) {
        val uid = requireUserId(userId)
        val docRef = db.collection("users").document(uid).collection("records").document(rec.date)
        val payload = mutableMapOf<String, Any?>(
            "date" to rec.date,
            "s" to rec.s,
            "userId" to uid,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        // With SetOptions.merge(), omitting a field leaves the old cloud value in place,
        // so a cleared value (e.g. OT "None") must be sent as an explicit delete.
        payload["sh"] = rec.sh ?: FieldValue.delete()
        payload["ot"] = rec.ot ?: FieldValue.delete()
        payload["inT"] = rec.inT ?: FieldValue.delete()
        payload["outT"] = rec.outT ?: FieldValue.delete()

        try {
            docRef.set(payload, SetOptions.merge()).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    override suspend fun deleteRecord(userId: String, date: String) {
        val uid = requireUserId(userId)
        val docRef = db.collection("users").document(uid).collection("records").document(date)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    override suspend fun clearAllRecords(userId: String) {
        val uid = requireUserId(userId)
        val colRef = db.collection("users").document(uid).collection("records")
        try {
            val docs = colRef.get().await()
            for (doc in docs.documents) {
                doc.reference.delete().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, colRef.path)
            throw e
        }
    }

    // Permanently deletes everything stored for this user: every document in the "records"
    // and "settings" subcollections, then the user document itself.
    // Must run BEFORE the Firebase Auth account is deleted (security rules need the signed-in user).
    // Reads use Source.SERVER so the call fails fast when offline instead of queuing deletes.
    override suspend fun deleteAllUserData(userId: String) {
        val uid = requireUserId(userId)
        val userDoc = db.collection("users").document(uid)
        try {
            for (sub in listOf("records", "settings")) {
                val docs = userDoc.collection(sub).get(Source.SERVER).await().documents
                for (chunk in docs.chunked(400)) { // batch limit is 500 writes
                    val batch = db.batch()
                    chunk.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }
            }
            userDoc.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, userDoc.path)
            throw e
        }
    }

    override fun observeConfig(userId: String): Flow<CloudSnap<Cfg?>> {
        val path = "users/$userId/settings/config"
        return db.collection("users").document(userId).collection("settings").document("config")
            .snapshotFlow()
            .map { doc ->
                val cfg: Cfg? = if (!doc.exists()) null
                else {
                    Cfg(
                        std = (doc.getLong("std") ?: 8L).toInt(),
                        currency = doc.getString("currency") ?: "₹",
                        dayRate = doc.getDouble("dayRate") ?: 500.0,
                        // Old accounts have no nightRate yet: night pay stays equal to day pay.
                        nightRate = doc.getDouble("nightRate") ?: (doc.getDouble("dayRate") ?: 500.0),
                        otHourRate = doc.getDouble("otHourRate") ?: 70.0,
                        festivalRate = doc.getDouble("festivalRate") ?: 500.0,
                        userId = userId
                    )
                }
                CloudSnap(cfg, doc.metadata.isFromCache)
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.GET, path)
                throw error.toCloud()
            }
    }

    override suspend fun saveConfig(userId: String, cfg: Cfg) {
        val uid = requireUserId(userId)
        val docRef = db.collection("users").document(uid).collection("settings").document("config")
        val payload = mapOf(
            "std" to cfg.std,
            "currency" to cfg.currency,
            "dayRate" to cfg.dayRate,
            "nightRate" to cfg.nightRate,
            "otHourRate" to cfg.otHourRate,
            "festivalRate" to cfg.festivalRate,
            "userId" to uid,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload, SetOptions.merge()).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }
}

private fun Query.snapshotFlow(): Flow<QuerySnapshot> = callbackFlow {
    val registration = addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
        if (error != null) close(error) else if (snapshot != null) trySend(snapshot)
    }
    awaitClose { registration.remove() }
}

private fun DocumentReference.snapshotFlow(): Flow<DocumentSnapshot> = callbackFlow {
    val registration = addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
        if (error != null) close(error) else if (snapshot != null) trySend(snapshot)
    }
    awaitClose { registration.remove() }
}

private fun Throwable.toCloud(): Throwable =
    if (this is FirebaseFirestoreException &&
        (code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
            code == FirebaseFirestoreException.Code.UNAUTHENTICATED)
    ) PermissionDeniedException(this) else this
