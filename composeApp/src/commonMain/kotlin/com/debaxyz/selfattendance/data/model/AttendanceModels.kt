package com.debaxyz.selfattendance.data.model

import com.debaxyz.selfattendance.platform.formatGrouped
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToLong

/** s: p=present, l=late, v=leave, w=weekly off, f=festival | sh: d/n | ot: overtime hours */
@Serializable
data class Rec(
    val date: String = "",
    val s: String = "p",
    val sh: String? = null,
    val ot: Double? = null,
    val inT: Long? = null,
    val outT: Long? = null,
    val userId: String = ""
)

@Serializable
data class Cfg(
    val std: Int = 8,
    val currency: String = "₹",
    val dayRate: Double = 500.0,   // money for one DAY-shift day
    val nightRate: Double = 500.0, // money for one NIGHT-shift day
    val otHourRate: Double = 150.0,
    val festivalRate: Double = 500.0,
    val userId: String = ""
)

data class MonthStats(
    val ym: YearMonth,
    val name: String,
    val totalDays: Int,
    val present: Int,
    val late: Int,
    val leave: Int,
    val festivalDays: Int,
    val hours: Long,
    val dayShifts: Int,
    val nightShifts: Int,
    val weeklyOff: Int,
    val overtime: Double,
    val baseMoney: Long,
    val otMoney: Long,
    val festivalMoney: Long,
    val totalMoney: Long
)

data class Totals(
    val present: Int,
    val dayShifts: Int,
    val nightShifts: Int,
    val overtime: Double,
    val hours: Long,
    val leave: Int,
    val weeklyOff: Int,
    val festivalDays: Int,
    val baseMoney: Long,
    val otMoney: Long,
    val festivalMoney: Long,
    val totalMoney: Long
)

val CURRENCIES = listOf("₹", "$", "€", "£", "AED", "SAR", "¥")
val OT_VALUES = listOf(0.0, 1.0, 2.0, 3.0, 4.0, 5.0)
val DOW = listOf("S", "M", "T", "W", "T", "F", "S")

internal val MONTHS_FULL = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)
private val DAYS_FULL = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

fun num(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()

/** One decimal, half-up, '.' separator (was String.format(Locale.US, "%.1f")). */
fun f1(d: Double): String {
    val r = floor(abs(d) * 10.0 + 0.5).toLong()
    val sign = if (d < 0 && r != 0L) "-" else ""
    return "$sign${r / 10}.${r % 10}"
}

/** Half-up like java.lang.Math.round. */
fun round1(d: Double): Double = floor(d * 10.0 + 0.5) / 10.0
fun money(n: Long): String = formatGrouped(n)
fun pad2(n: Int) = n.toString().padStart(2, '0')

fun nowLocal(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
fun LocalDateTime.hms() = "${pad2(hour)}:${pad2(minute)}:${pad2(second)}"

/**
 * Supports the tokens this app uses: EEEE/EEE, d/dd, MMMM/MMM/MM/M, yyyy/yy.
 * Month/day names are English (java.time used the device locale).
 */
fun fmtDate(d: LocalDate, pattern: String): String {
    val sb = StringBuilder()
    var i = 0
    while (i < pattern.length) {
        val c = pattern[i]
        var j = i
        while (j < pattern.length && pattern[j] == c) j++
        val n = j - i
        sb.append(
            when (c) {
                'E' -> DAYS_FULL[d.dayOfWeek.isoDayNumber - 1].let { if (n >= 4) it else it.take(3) }
                'd' -> if (n >= 2) pad2(d.dayOfMonth) else d.dayOfMonth.toString()
                'M' -> when {
                    n >= 4 -> MONTHS_FULL[d.monthNumber - 1]
                    n == 3 -> MONTHS_FULL[d.monthNumber - 1].take(3)
                    n == 2 -> pad2(d.monthNumber)
                    else -> d.monthNumber.toString()
                }
                'y' -> if (n == 2) pad2(d.year % 100) else d.year.toString()
                else -> pattern.substring(i, j)
            }
        )
        i = j
    }
    return sb.toString()
}

/** "h:mm a" in the device time zone. */
fun fmtTime(ms: Long): String {
    val t = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())
    val h12 = if (t.hour % 12 == 0) 12 else t.hour % 12
    return "$h12:${pad2(t.minute)} ${if (t.hour < 12) "AM" else "PM"}"
}

fun workedHours(r: Rec?, std: Int): Double {
    if (r == null) return 0.0
    if (r.inT != null && r.outT != null) return (r.outT - r.inT) / 3.6e6
    if (r.s == "p" || r.s == "l") return std + (r.ot ?: 0.0)
    return 0.0
}

fun monthStats(ym: YearMonth, data: Map<String, Rec>, cfg: Cfg): MonthStats {
    var p = 0; var lt = 0; var v = 0; var h = 0.0; var d = 0; var n = 0; var o = 0; var f = 0
    var ot = 0.0
    val days = ym.lengthOfMonth()
    for (day in 1..days) {
        val e = data[ym.atDay(day).toString()] ?: continue
        val work = e.s != "v" && e.s != "w" && e.s != "f"
        if (e.s == "w") o++
        if (e.s == "f") f++
        if (work) ot += e.ot ?: max(0.0, workedHours(e, cfg.std) - cfg.std)
        when (e.s) { "p" -> p++; "l" -> lt++; "v" -> v++ }
        h += workedHours(e, cfg.std)
        if (work) { if (e.sh == "n") n++ else d++ }
    }
    val present = p + lt
    val base = d * cfg.dayRate + n * cfg.nightRate
    val otM = ot * cfg.otHourRate
    val fM = f * cfg.festivalRate
    return MonthStats(
        ym, ym.displayName(), days,
        present, lt, v, f, h.roundToLong(), d, n, o, round1(ot),
        base.roundToLong(), otM.roundToLong(), fM.roundToLong(), (base + otM + fM).roundToLong()
    )
}

fun totals(list: List<MonthStats>) = Totals(
    list.sumOf { it.present }, list.sumOf { it.dayShifts }, list.sumOf { it.nightShifts },
    list.sumOf { it.overtime }, list.sumOf { it.hours }, list.sumOf { it.leave },
    list.sumOf { it.weeklyOff }, list.sumOf { it.festivalDays },
    list.sumOf { it.baseMoney }, list.sumOf { it.otMoney },
    list.sumOf { it.festivalMoney }, list.sumOf { it.totalMoney }
)
