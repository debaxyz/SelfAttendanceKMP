package com.debaxyz.selfattendance.data.model

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/** Minimal replacement for java.time.YearMonth (kotlinx-datetime has none). */
data class YearMonth(val year: Int, val month: Int) : Comparable<YearMonth> {
    init { require(month in 1..12) { "month must be 1..12" } }

    val monthValue: Int get() = month

    fun atDay(day: Int): LocalDate = LocalDate(year, month, day)

    fun lengthOfMonth(): Int = when (month) {
        2 -> if (isLeap(year)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    fun plusMonths(n: Int): YearMonth {
        val idx = year * 12 + (month - 1) + n
        return YearMonth(idx.floorDiv(12), idx.mod(12) + 1)
    }

    fun minusMonths(n: Int) = plusMonths(-n)

    /** "MMMM yyyy", e.g. "March 2026" */
    fun displayName(): String = "${MONTHS_FULL[month - 1]} $year"
    fun shortMonthName(): String = MONTHS_FULL[month - 1].take(3)

    override fun compareTo(other: YearMonth) = compareValuesBy(this, other, { it.year }, { it.month })

    /** ISO form "yyyy-MM", same as java.time.YearMonth.toString(). */
    override fun toString(): String = "${year.toString().padStart(4, '0')}-${pad2(month)}"

    companion object {
        private fun isLeap(y: Int) = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0

        fun parse(s: String): YearMonth {
            val p = s.split("-")
            require(p.size >= 2) { "Bad year-month: $s" }
            return YearMonth(p[0].toInt(), p[1].toInt())
        }
        fun from(d: LocalDate) = YearMonth(d.year, d.monthNumber)
        fun from(d: LocalDateTime) = from(d.date)
        fun now(): YearMonth = from(Clock.System.todayIn(TimeZone.currentSystemDefault()))
    }
}
