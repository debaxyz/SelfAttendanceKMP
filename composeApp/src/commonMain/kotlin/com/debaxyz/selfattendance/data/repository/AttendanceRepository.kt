package com.debaxyz.selfattendance.data.repository

import com.debaxyz.selfattendance.data.model.Cfg
import com.debaxyz.selfattendance.data.model.Rec
import kotlinx.coroutines.flow.Flow

/** A snapshot plus whether it came from the local cache (true) or was confirmed by the server (false). */
data class CloudSnap<T>(val value: T, val fromCache: Boolean)

/** Thrown by a cloud repository when the backend refuses access (security rules / signed out). Not retried. */
class PermissionDeniedException(cause: Throwable? = null) : Exception(cause?.message, cause)

interface AttendanceRepository {
    fun observeRecords(userId: String): Flow<CloudSnap<Map<String, Rec>>>
    suspend fun saveRecord(userId: String, rec: Rec)
    suspend fun deleteRecord(userId: String, date: String)
    suspend fun clearAllRecords(userId: String)
    suspend fun deleteAllUserData(userId: String)
    fun observeConfig(userId: String): Flow<CloudSnap<Cfg?>>
    suspend fun saveConfig(userId: String, cfg: Cfg)
}
