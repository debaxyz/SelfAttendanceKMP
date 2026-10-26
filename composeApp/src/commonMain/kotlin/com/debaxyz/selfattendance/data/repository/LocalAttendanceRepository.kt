package com.debaxyz.selfattendance.data.repository

import com.debaxyz.selfattendance.data.model.Cfg
import com.debaxyz.selfattendance.data.model.Rec
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Desktop and Wasm: records and settings are kept on this device only (java prefs / browser localStorage). */
class LocalAttendanceRepository(private val settings: Settings = Settings()) : AttendanceRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val recordsSer = MapSerializer(String.serializer(), Rec.serializer())

    private val records = MutableStateFlow(loadRecords())
    private val config = MutableStateFlow(loadConfig())

    private fun loadRecords(): Map<String, Rec> = try {
        settings.getStringOrNull(KEY_RECORDS)?.let { json.decodeFromString(recordsSer, it) } ?: emptyMap()
    } catch (_: Exception) { emptyMap() }

    private fun loadConfig(): Cfg? = try {
        settings.getStringOrNull(KEY_CONFIG)?.let { json.decodeFromString(Cfg.serializer(), it) }
    } catch (_: Exception) { null }

    private fun persistRecords() = settings.putString(KEY_RECORDS, json.encodeToString(recordsSer, records.value))

    override fun observeRecords(userId: String): Flow<CloudSnap<Map<String, Rec>>> =
        records.map { CloudSnap(it, fromCache = false) }

    override suspend fun saveRecord(userId: String, rec: Rec) {
        records.value = records.value + (rec.date to rec)
        persistRecords()
    }

    override suspend fun deleteRecord(userId: String, date: String) {
        records.value = records.value - date
        persistRecords()
    }

    override suspend fun clearAllRecords(userId: String) {
        records.value = emptyMap()
        persistRecords()
    }

    override suspend fun deleteAllUserData(userId: String) {
        records.value = emptyMap()
        config.value = null
        settings.remove(KEY_RECORDS)
        settings.remove(KEY_CONFIG)
    }

    override fun observeConfig(userId: String): Flow<CloudSnap<Cfg?>> =
        config.map { CloudSnap(it, fromCache = false) }

    override suspend fun saveConfig(userId: String, cfg: Cfg) {
        config.value = cfg
        settings.putString(KEY_CONFIG, json.encodeToString(Cfg.serializer(), cfg))
    }

    private companion object {
        const val KEY_RECORDS = "local_records"
        const val KEY_CONFIG = "local_config"
    }
}
