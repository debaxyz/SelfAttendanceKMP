package com.debaxyz.selfattendance.ui

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.debaxyz.selfattendance.data.auth.AppUser
import com.debaxyz.selfattendance.data.model.*
import com.debaxyz.selfattendance.data.repository.PermissionDeniedException
import com.debaxyz.selfattendance.data.repository.AttendanceRepository
import com.debaxyz.selfattendance.platform.PlatformServices
import com.debaxyz.selfattendance.platform.clear
import com.debaxyz.selfattendance.ui.auth.deleteAccountErrorMessage
import com.debaxyz.selfattendance.ui.auth.reauthenticateWithGoogle
import com.debaxyz.selfattendance.ui.auth.reauthenticateWithPassword
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.math.max

class AttendanceViewModel(private val services: PlatformServices) : ViewModel() {
    private val prefs: Settings = Settings()
    private val repository: AttendanceRepository = services.repository
    private val auth = services.auth

    var currentUser by mutableStateOf<AppUser?>(auth.currentUser)
    var isSyncing by mutableStateOf(false)
    var showProfileDialog by mutableStateOf(false)

    var tab by mutableStateOf("home")
    // No local copy: records and settings live in memory and in Firestore only.
    var data by mutableStateOf<Map<String, Rec>>(emptyMap())
    var cfg by mutableStateOf(Cfg())
    var shift by mutableStateOf(prefs.getStringOrNull("shift").let { if (it == "n") "n" else "d" })
    var now by mutableStateOf(nowLocal())
    val today by derivedStateOf { now.date }
    var viewMonth by mutableStateOf(YearMonth.now())
    var editingKey by mutableStateOf<String?>(null)
    var showReset by mutableStateOf(false)
    var showDeleteAccount by mutableStateOf(false)
    var isDeletingAccount by mutableStateOf(false)
    var deleteAccountError by mutableStateOf<String?>(null)
    var filter by mutableStateOf("all")
    var themeMode by mutableStateOf(prefs.getStringOrNull("theme")?.takeIf { it == "light" || it == "dark" })

    val toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    fun showToast(m: String) { toasts.tryEmit(m) }

    private var cloudRecordsJob: Job? = null
    private var cloudConfigJob: Job? = null

    init {
        // Remove the old on-device copy left by earlier versions.
        prefs.remove("data")
        prefs.remove("cfg")

        // Listen to Auth State
        viewModelScope.launch {
            auth.authState.collect { user ->
                currentUser = user
                if (user != null) {
                    startCloudSync(user.uid)
                } else {
                    stopCloudSync()
                    // Never keep one account's data in memory for the next sign-in.
                    data = emptyMap()
                    cfg = Cfg()
                }
            }
        }

        // Ticker for clock
        viewModelScope.launch {
            while (true) {
                delay(1000)
                now = nowLocal()
            }
        }
    }

    /**
     * Re-subscribes a Firestore listener after a transient failure (network drop, server hiccup)
     * with a growing delay, so live updates resume by themselves. Permission errors are not retried.
     */
    private fun <T> Flow<T>.autoRetry(): Flow<T> = retryWhen { cause, attempt ->
        if (cause is PermissionDeniedException) {
            false
        } else {
            println("CloudSync: Listener failed, retrying (attempt ${attempt + 1}): ${cause.message}")
            delay(minOf(30_000L, 2_000L * (attempt + 1)))
            true
        }
    }

    private fun startCloudSync(userId: String) {
        stopCloudSync()
        isSyncing = true

        // Live records: every change made on any device (or by a delete) is applied here instantly.
        cloudRecordsJob = viewModelScope.launch {
            var initialized = false
            try {
                repository.observeRecords(userId).autoRetry().collectLatest { snap ->
                    val cloud = snap.value
                    if (!initialized) {
                        if (snap.fromCache) {
                            // Cache is not authoritative yet: show it, but wait for the server
                            // before deciding the cloud is empty.
                            if (cloud.isNotEmpty() && data != cloud) {
                                data = cloud
                            }
                            return@collectLatest
                        }
                        initialized = true
                    }
                    // From here on the cloud is the source of truth, including deletions.
                    if (data != cloud) {
                        data = cloud
                    }
                    isSyncing = false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                isSyncing = false
                showToast(syncErrorMessage(e))
            }
        }

        cloudConfigJob = viewModelScope.launch {
            var initialized = false
            try {
                repository.observeConfig(userId).autoRetry().collectLatest { snap ->
                    val cloudCfg = snap.value
                    if (cloudCfg != null) {
                        initialized = true
                        if (cfg != cloudCfg) {
                            cfg = cloudCfg
                        }
                    } else if (!snap.fromCache && !initialized) {
                        initialized = true
                        // No config in the cloud yet: create it with the current (default) values.
                        repository.saveConfig(userId, cfg.copy(userId = userId))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    private fun syncErrorMessage(e: Exception): String =
        if (e is PermissionDeniedException)
            "Cloud sync is not permitted. Check the Firestore security rules."
        else "Cloud sync stopped. Please check your connection."

    private fun stopCloudSync() {
        cloudRecordsJob?.cancel()
        cloudRecordsJob = null
        cloudConfigJob?.cancel()
        cloudConfigJob = null
        isSyncing = false
    }

    /* ---- Backup / restore JSON helpers (no on-device data copy is kept) ---- */
    private val prettyJson = Json { prettyPrint = true }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.dbl(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.lng(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

    private fun parseRecords(o: JsonObject): Map<String, Rec> {
        val m = LinkedHashMap<String, Rec>()
        o.forEach { (k, value) ->
            val e = value as? JsonObject ?: return@forEach
            m[k] = Rec(
                date = k,
                s = e.str("s") ?: "p",
                sh = e.str("sh"),
                ot = e.dbl("ot"),
                inT = e.lng("in"),
                outT = e.lng("out")
            )
        }
        return m
    }

    private fun recordsJson(d: Map<String, Rec>): JsonObject = buildJsonObject {
        d.forEach { (k, r) ->
            put(k, buildJsonObject {
                put("s", r.s)
                r.sh?.let { put("sh", it) }
                r.ot?.let { put("ot", it) }
                r.inT?.let { put("in", it) }
                r.outT?.let { put("out", it) }
            })
        }
    }

    private fun cfgJson(c: Cfg): JsonObject = buildJsonObject {
        put("std", c.std)
        put("currency", c.currency)
        put("dayRate", c.dayRate)
        put("nightRate", c.nightRate)
        put("otHourRate", c.otHourRate)
        put("festivalRate", c.festivalRate)
    }

    private fun cfgFrom(o: JsonObject, b: Cfg = Cfg()) = Cfg(
        std = o.int("std") ?: b.std,
        currency = o.str("currency") ?: b.currency,
        dayRate = o.dbl("dayRate") ?: b.dayRate,
        nightRate = o.dbl("nightRate") ?: (o.dbl("dayRate") ?: b.dayRate),
        otHourRate = o.dbl("otHourRate") ?: b.otHourRate,
        festivalRate = o.dbl("festivalRate") ?: b.festivalRate
    )

    fun persistData(d: Map<String, Rec>) {
        data = d
    }

    fun persistConfig(c: Cfg) {
        cfg = c
        val uid = currentUser?.uid
        if (uid != null) {
            viewModelScope.launch {
                try {
                    repository.saveConfig(uid, c.copy(userId = uid))
                } catch (_: Exception) {}
            }
        }
    }

    fun toggleTheme(systemDark: Boolean) {
        val next = themeMode?.let { if (it == "dark") "light" else "dark" } ?: if (systemDark) "light" else "dark"
        themeMode = next
        prefs.putString("theme", next)
    }

    /* ---- Editing ---- */
    private fun ensureRecord(): Rec {
        val k = editingKey ?: return Rec(date = today.toString(), s = "p", sh = shift)
        val r = data[k]
        return if (r == null || r.s == "v" || r.s == "w") Rec(date = k, s = "p", sh = shift) else r.copy(date = k)
    }

    fun setEdit(rec: Rec) {
        val k = editingKey ?: return
        val finalRec = rec.copy(date = k)
        persistData(data + (k to finalRec))
        val uid = currentUser?.uid
        if (uid != null) {
            viewModelScope.launch {
                try {
                    repository.saveRecord(uid, finalRec.copy(userId = uid))
                } catch (e: Exception) {
                    showToast("Cloud sync pending")
                }
            }
        }
    }

    fun chooseShift(sh: String) = setEdit(ensureRecord().copy(sh = sh))
    fun setOT(v: Double) = setEdit(ensureRecord().copy(ot = if (v > 0) v else null))
    fun setFestival() = setEdit(ensureRecord().copy(s = "f"))

    fun clearDay() {
        val k = editingKey ?: return
        persistData(data - k)
        val uid = currentUser?.uid
        if (uid != null) {
            viewModelScope.launch {
                try {
                    repository.deleteRecord(uid, k)
                } catch (_: Exception) {}
            }
        }
    }

    fun clearAllAttendance() {
        persistData(emptyMap())
        val uid = currentUser?.uid
        if (uid != null) {
            viewModelScope.launch {
                try {
                    repository.clearAllRecords(uid)
                } catch (_: Exception) {}
            }
        }
        showToast("All attendance records cleared.")
    }

    fun updateStd(delta: Int) {
        val h = (cfg.std + delta).coerceIn(1, 16)
        persistConfig(cfg.copy(std = h))
        showToast("Standard shift hours set to ${h}h")
    }

    fun setRate(key: String, value: Double) {
        val v = if (value.isNaN()) 0.0 else max(0.0, value)
        persistConfig(
            when (key) {
                "dayRate" -> cfg.copy(dayRate = v)
                "nightRate" -> cfg.copy(nightRate = v)
                "otHourRate" -> cfg.copy(otHourRate = v)
                else -> cfg.copy(festivalRate = v)
            }
        )
    }

    fun allMonths(): List<MonthStats> {
        val set = mutableSetOf<String>()
        set.add(YearMonth.from(now).toString())
        set.add(viewMonth.toString())
        data.keys.forEach { k ->
            val p = k.split("-")
            if (p.size >= 2) set.add("${p[0]}-${p[1]}")
        }
        return set.sortedDescending().mapNotNull {
            runCatching { monthStats(YearMonth.parse(it), data, cfg) }.getOrNull()
        }
    }

    /* ---- Exports & Backup ---- */
    fun monthCsv(ym: YearMonth): Triple<String, String, String> {
        val mName = ym.displayName()
        val ms = monthStats(ym, data, cfg)
        val c = cfg.currency
        val rows = mutableListOf<String>()
        val nowD = nowLocal()
        rows += "\"Self Attendance Monthly Report & Salary - $mName\""
        rows += "\"Generated on: ${nowD.date} ${nowD.hms()}\""
        rows += "\"User Account: ${currentUser?.email ?: "Local User"}\""
        rows += ""
        rows += "\"Date\",\"Day\",\"Status\",\"Shift\",\"Overtime (h)\",\"Total Hours Worked\",\"Details\""
        for (d in 1..ym.lengthOfMonth()) {
            val date = ym.atDay(d)
            val k = date.toString()
            val e = data[k]
            val dayName = fmtDate(date, "EEE")
            var st = "Not Marked"; var sh = "-"; var ot = "0.0"; var hr = "0.0"; var note = ""
            if (e != null) {
                when (e.s) {
                    "v" -> st = "Leave"
                    "w" -> st = "Weekly Off"
                    "f" -> { st = "Festival Day"; sh = if (e.sh == "n") "Night" else "Day" }
                    else -> {
                        st = if (e.s == "l") "Late" else "Present"
                        sh = if (e.sh == "n") "Night Shift" else "Day Shift"
                        ot = f1(e.ot ?: max(0.0, workedHours(e, cfg.std) - cfg.std))
                        hr = f1(workedHours(e, cfg.std))
                        note = if (e.inT != null)
                            "In: ${fmtTime(e.inT)}" + (if (e.outT != null) " - Out: ${fmtTime(e.outT)}" else "")
                        else "Manual"
                    }
                }
            }
            rows += "\"$k\",\"$dayName\",\"$st\",\"$sh\",\"$ot\",\"$hr\",\"$note\""
        }
        rows += listOf(
            "", "\"=== MONTH TOTAL SUMMARY ===\"",
            "\"Month\",\"$mName\"", "\"Total Calendar Days\",\"${ms.totalDays}\"",
            "\"Total Present Days\",\"${ms.present}\"", "\"Festival Days\",\"${ms.festivalDays}\"",
            "\"Day Shifts\",\"${ms.dayShifts}\"", "\"Night Shifts\",\"${ms.nightShifts}\"",
            "\"Late Arrivals\",\"${ms.late}\"", "\"Leave Days\",\"${ms.leave}\"",
            "\"Weekly Offs\",\"${ms.weeklyOff}\"", "\"Total Overtime (h)\",\"${f1(ms.overtime)}\"",
            "\"Total Hours Worked\",\"${ms.hours}\"", "", "\"=== SALARY & WAGE BREAKDOWN ===\"",
            "\"Day Shift Money Rate\",\"$c${num(cfg.dayRate)}\"",
            "\"Night Shift Money Rate\",\"$c${num(cfg.nightRate)}\"",
            "\"One Hour OT Money Rate\",\"$c${num(cfg.otHourRate)}\"",
            "\"One Day Festival Money Rate\",\"$c${num(cfg.festivalRate)}\"",
            "\"Base Pay (${ms.dayShifts} day + ${ms.nightShifts} night shifts)\",\"$c${ms.baseMoney}\"",
            "\"Overtime Pay (${f1(ms.overtime)}h)\",\"$c${ms.otMoney}\"",
            "\"Festival Pay (${ms.festivalDays} days)\",\"$c${ms.festivalMoney}\"",
            "\"TOTAL ESTIMATED EARNINGS\",\"$c${ms.totalMoney}\""
        )
        val mon = ym.shortMonthName()
        return Triple(
            "Attendance_Report_${ym.year}_${pad2(ym.monthValue)}_$mon.csv",
            "\uFEFF" + rows.joinToString("\r\n"),
            "Downloaded CSV report for $mName"
        )
    }

    fun allCsv(): Triple<String, String, String> {
        val c = cfg.currency
        val list = allMonths()
        val t = totals(list)
        val nowD = nowLocal()
        val rows = mutableListOf(
            "\"Self Attendance - All Months Total Summary & Earnings Report\"",
            "\"Export Date: ${nowD.date} ${nowD.hms()}\"",
            "\"User: ${currentUser?.email ?: "Local User"}\"",
            "\"Rates Configured: Day = $c${num(cfg.dayRate)} | Night = $c${num(cfg.nightRate)} | 1h OT = $c${num(cfg.otHourRate)} | 1 Day Festival = $c${num(cfg.festivalRate)}\"",
            "",
            "\"Month\",\"Present Days\",\"Day Shifts\",\"Night Shifts\",\"Festival\",\"Late\",\"Leave\",\"Weekly Off\",\"Overtime (h)\",\"Total Hours\",\"Base Money\",\"OT Money\",\"Festival Money\",\"Total Salary\""
        )
        list.forEach { m ->
            rows += "\"${m.name}\",\"${m.present}\",\"${m.dayShifts}\",\"${m.nightShifts}\",\"${m.festivalDays}\",\"${m.late}\",\"${m.leave}\",\"${m.weeklyOff}\",\"${f1(m.overtime)}\",\"${m.hours}\",\"$c${m.baseMoney}\",\"$c${m.otMoney}\",\"$c${m.festivalMoney}\",\"$c${m.totalMoney}\""
        }
        rows += ""
        rows += "\"TOTAL ALL-TIME\",\"${t.present}\",\"${t.dayShifts}\",\"${t.nightShifts}\",\"${t.festivalDays}\",\"-\",\"${t.leave}\",\"${t.weeklyOff}\",\"${f1(t.overtime)}\",\"${t.hours}\",\"$c${t.baseMoney}\",\"$c${t.otMoney}\",\"$c${t.festivalMoney}\",\"$c${t.totalMoney}\""
        return Triple(
            "Attendance_All_Months_Summary.csv",
            "\uFEFF" + rows.joinToString("\r\n"),
            "Downloaded All Months Total Summary CSV"
        )
    }

    fun backupJson(): Triple<String, String, String> {
        val o = buildJsonObject {
            put("version", 1)
            put("exportedAt", Clock.System.now().toString())
            put("userEmail", currentUser?.email)
            put("config", cfgJson(cfg))
            put("records", recordsJson(data))
        }
        val day = Clock.System.todayIn(TimeZone.currentSystemDefault())
        return Triple("Attendance_Backup_$day.json", prettyJson.encodeToString(JsonObject.serializer(), o), "Backup JSON exported successfully")
    }

    fun restore(text: String) {
        try {
            val p = Json.parseToJsonElement(text).jsonObject
            val rec = p["records"] as? JsonObject
            if (rec != null) {
                val parsed = parseRecords(rec)
                persistData(parsed)
                (p["config"] as? JsonObject)?.let { persistConfig(cfgFrom(it)) }
                val uid = currentUser?.uid
                if (uid != null) {
                    viewModelScope.launch {
                        for ((_, r) in parsed) {
                            repository.saveRecord(uid, r.copy(userId = uid))
                        }
                    }
                }
                showToast("Data restored successfully from backup!")
            } else {
                showToast("Invalid backup file format.")
            }
        } catch (_: Exception) {
            showToast("Error reading backup file.")
        }
    }

    /**
     * Deletes the account and ALL its data:
     * 1) re-verify identity (Firebase needs a recent login), 2) stop cloud listeners so they
     * cannot re-upload local data, 3) delete Firestore data, 4) delete the Auth user,
     * 5) wipe local data. On success the auth listener returns the app to the sign-in screen.
     */
    fun deleteAccount(password: String?) {
        val user = auth.currentUser ?: return
        if (isDeletingAccount) return
        val uid = user.uid
        isDeletingAccount = true
        deleteAccountError = null
        viewModelScope.launch {
            var syncStopped = false
            try {
                val reauth = if (user.usesGoogle) reauthenticateWithGoogle(services.google, auth)
                else reauthenticateWithPassword(auth, password.orEmpty())
                reauth.getOrThrow()

                stopCloudSync()
                syncStopped = true

                repository.deleteAllUserData(uid)
                auth.deleteCurrentUser()

                data = emptyMap()
                cfg = Cfg()
                showDeleteAccount = false
                showProfileDialog = false
                try { services.google.clear() } catch (_: Exception) {}
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Auth: Account deletion failed: ${e.message}")
                deleteAccountError = deleteAccountErrorMessage(e)
                if (syncStopped && auth.currentUser != null) startCloudSync(uid)
            } finally {
                isDeletingAccount = false
            }
        }
    }

    fun signOut(onComplete: () -> Unit = {}) {
        com.debaxyz.selfattendance.ui.auth.signOut(services.google, auth, onComplete)
    }
}
