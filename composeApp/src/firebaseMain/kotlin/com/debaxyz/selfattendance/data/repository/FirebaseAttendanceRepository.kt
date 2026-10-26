package com.debaxyz.selfattendance.data.repository

import com.debaxyz.selfattendance.data.model.Cfg
import com.debaxyz.selfattendance.data.model.Rec
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.FirestoreExceptionCode
import dev.gitlive.firebase.firestore.code
import dev.gitlive.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable
internal data class RecordDoc(
    val date: String,
    val s: String,
    val userId: String,
    val sh: String? = null,
    val ot: Double? = null,
    val inT: Long? = null,
    val outT: Long? = null
)

@Serializable
internal data class ConfigDoc(
    val std: Int,
    val currency: String,
    val dayRate: Double,
    val nightRate: Double,
    val otHourRate: Double,
    val festivalRate: Double,
    val userId: String
)

class FirebaseAttendanceRepository(
    private val db: FirebaseFirestore
) : AttendanceRepository {
    private val auth get() = Firebase.auth

    private fun requireUserId(expectedUserId: String? = null): String {
        val currentUid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before accessing Firestore.")
        if (expectedUserId != null && currentUid != expectedUserId) {
            throw IllegalStateException("User session mismatch: $currentUid != $expectedUserId")
        }
        return currentUid
    }

    private fun records(uid: String) = db.collection("users").document(uid).collection("records")
    private fun configDoc(uid: String) = db.collection("users").document(uid).collection("settings").document("config")

    override fun observeRecords(userId: String): Flow<CloudSnap<Map<String, Rec>>> {
        val path = "users/$userId/records"
        return records(userId)
            .snapshots(includeMetadataChanges = true)
            .map { snapshot ->
                val result = LinkedHashMap<String, Rec>()
                for (doc in snapshot.documents) {
                    result[doc.id] = Rec(
                        date = doc.id,
                        s = doc.get<String?>("s") ?: "p",
                        sh = doc.get<String?>("sh"),
                        ot = doc.get<Double?>("ot"),
                        inT = doc.get<Long?>("inT"),
                        outT = doc.get<Long?>("outT"),
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
        val docRef = records(uid).document(rec.date)
        try {
            // merge = true leaves omitted fields untouched, so a cleared value (e.g. OT "None")
            // must be removed with an explicit FieldValue.delete in the follow-up update.
            docRef.set(
                RecordDoc(rec.date, rec.s, uid, rec.sh, rec.ot, rec.inT, rec.outT),
                merge = true
            )
            val follow = mutableListOf<Pair<String, Any?>>("updatedAt" to FieldValue.serverTimestamp)
            if (rec.sh == null) follow += "sh" to FieldValue.delete
            if (rec.ot == null) follow += "ot" to FieldValue.delete
            if (rec.inT == null) follow += "inT" to FieldValue.delete
            if (rec.outT == null) follow += "outT" to FieldValue.delete
            docRef.update(*follow.toTypedArray())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid/records/${rec.date}")
            throw e
        }
    }

    override suspend fun deleteRecord(userId: String, date: String) {
        val uid = requireUserId(userId)
        val docRef = records(uid).document(date)
        try {
            docRef.delete()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid/records/$date")
            throw e
        }
    }

    override suspend fun clearAllRecords(userId: String) {
        val uid = requireUserId(userId)
        val colRef = records(uid)
        try {
            val docs = colRef.get()
            for (doc in docs.documents) {
                doc.reference.delete()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid/records")
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
                val docs = userDoc.collection(sub).get(Source.SERVER).documents
                for (chunk in docs.chunked(400)) { // batch limit is 500 writes
                    val batch = db.batch()
                    chunk.forEach { batch.delete(it.reference) }
                    batch.commit()
                }
            }
            userDoc.delete()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid")
            throw e
        }
    }

    override fun observeConfig(userId: String): Flow<CloudSnap<Cfg?>> {
        val path = "users/$userId/settings/config"
        return configDoc(userId)
            .snapshots(includeMetadataChanges = true)
            .map { doc ->
                val cfg: Cfg? = if (!doc.exists) null
                else {
                    val dayRate = doc.get<Double?>("dayRate") ?: 500.0
                    Cfg(
                        std = (doc.get<Long?>("std") ?: 8L).toInt(),
                        currency = doc.get<String?>("currency") ?: "₹",
                        dayRate = dayRate,
                        // Old accounts have no nightRate yet: night pay stays equal to day pay.
                        nightRate = doc.get<Double?>("nightRate") ?: dayRate,
                        otHourRate = doc.get<Double?>("otHourRate") ?: 70.0,
                        festivalRate = doc.get<Double?>("festivalRate") ?: 500.0,
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
        val docRef = configDoc(uid)
        try {
            docRef.set(
                ConfigDoc(cfg.std, cfg.currency, cfg.dayRate, cfg.nightRate, cfg.otHourRate, cfg.festivalRate, uid),
                merge = true
            )
            docRef.update("updatedAt" to FieldValue.serverTimestamp)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid/settings/config")
            throw e
        }
    }
}

private fun Throwable.toCloud(): Throwable =
    if (this is FirebaseFirestoreException &&
        (code == FirestoreExceptionCode.PERMISSION_DENIED || code == FirestoreExceptionCode.UNAUTHENTICATED)
    ) PermissionDeniedException(this) else this
