package com.archite.piecesoflife.util

import java.util.Calendar
import java.util.TimeZone

object TimeUtil {

    const val TIME_TYPE_DATE = 0
    const val TIME_TYPE_SECOND = 1
    const val TIME_TYPE_HOUR = 2
    const val TIME_TYPE_DATE_WEEK = 3

    const val QUEST_TYPE_DEFAULT = -1
    const val QUEST_TYPE_MONTH = 0
    const val QUEST_TYPE_WEEK = 1
    const val QUEST_TYPE_DAY = 2

    const val LOG_DISPLAY_MODE_DAY = 2
    const val LOG_DISPLAY_MODE_WEEK = 1
    const val LOG_DISPLAY_MODE_MONTH = 0

    fun getTimeString(type: Int): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+08:00"))
        val yc = arrayOf("周六", "周日", "周一", "周二", "周三", "周四", "周五", "周六")

        return when (type) {
            TIME_TYPE_DATE -> "${calendar.get(Calendar.YEAR)}年${trans(calendar.get(Calendar.MONTH) + 1)}月${trans(calendar.get(Calendar.DAY_OF_MONTH))}日"
            TIME_TYPE_SECOND -> "${trans(calendar.get(Calendar.HOUR_OF_DAY))}:${trans(calendar.get(Calendar.MINUTE))}:${trans(calendar.get(Calendar.SECOND))}"
            TIME_TYPE_HOUR -> "${trans(calendar.get(Calendar.HOUR_OF_DAY))}:${trans(calendar.get(Calendar.MINUTE))}"
            TIME_TYPE_DATE_WEEK -> "${calendar.get(Calendar.YEAR)}年${trans(calendar.get(Calendar.MONTH) + 1)}月${trans(calendar.get(Calendar.DATE))}日 ${yc[calendar.get(Calendar.DAY_OF_WEEK)]}"
            else -> ""
        }
    }

    fun getTimeInt(): Int {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+08:00"))
        return getTimeInt(TIME_TYPE_DATE, calendar)
    }

    fun getTimeInt(type: Int): Int {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+08:00"))
        return getTimeInt(type, calendar)
    }

    fun getTimeInt(calendar: Calendar): Int {
        return getTimeInt(TIME_TYPE_DATE, calendar)
    }

    fun getTimeInt(type: Int, calendar: Calendar): Int {
        return when (type) {
            TIME_TYPE_DATE -> calendar.get(Calendar.YEAR) * 10000 + (calendar.get(Calendar.MONTH) + 1) * 100 + calendar.get(Calendar.DAY_OF_MONTH)
            TIME_TYPE_SECOND -> calendar.get(Calendar.HOUR_OF_DAY) * 10000 + calendar.get(Calendar.MINUTE) * 100 + calendar.get(Calendar.SECOND)
            else -> 0
        }
    }

    fun timeInt2String(timeInt: Int): String {
        return "${trans(timeInt / 10000)}:${trans((timeInt % 10000) / 100)} "
    }

    fun dateInt2String(dateInt: Int): String {
        return if (dateInt / 10000 == 0)
            "${(dateInt % 10000) / 100}月${dateInt % 100}日 "
        else
            "${dateInt / 10000}年${(dateInt % 10000) / 100}月${dateInt % 100}日"
    }

    fun dateInt2StringSimple(dateInt: Int): String {
        return if (dateInt / 10000 == 0)
            "${(dateInt % 10000) / 100}/${dateInt % 100} "
        else
            "${dateInt / 10000}/${(dateInt % 10000) / 100}/${dateInt % 100}"
    }

    fun dateIntMinus(dateInt1: Int, dateInt2: Int): Int {
        val calendar1 = Calendar.getInstance()
        val calendar2 = calendar1.clone() as Calendar
        calendar1.set(dateInt1 / 10000, ((dateInt1 / 100) % 100) - 1, dateInt1 % 100)
        calendar2.set(dateInt2 / 10000, ((dateInt2 / 100) % 100) - 1, dateInt2 % 100)

        if (dateInt2 > 30000000) return 999999

        var ans = 0
        while (calendar2.after(calendar1)) {
            calendar1.add(Calendar.DAY_OF_MONTH, 1)
            ans++
        }
        return ans
    }

    fun calDateInterval(date: Int, groupDate: Int): IntArray {
        val timeInterval = IntArray(2)
        val calendar = Calendar.getInstance()
        val y = date / 10000
        val m = ((date / 100) % 100) - 1
        val d = date % 100

        when (groupDate) {
            LOG_DISPLAY_MODE_DAY -> {
                timeInterval[0] = date
                timeInterval[1] = date
            }
            LOG_DISPLAY_MODE_MONTH -> {
                calendar.set(y, m, 1)
                timeInterval[0] = getTimeInt(calendar)
                calendar.add(Calendar.MONTH, 1)
                calendar.add(Calendar.DAY_OF_MONTH, -1)
                timeInterval[1] = getTimeInt(calendar)
            }
            LOG_DISPLAY_MODE_WEEK -> {
                calendar.set(y, m, d)
                val dayOfWeek = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
                calendar.add(Calendar.DAY_OF_MONTH, -dayOfWeek)
                timeInterval[0] = getTimeInt(calendar)
                calendar.add(Calendar.DAY_OF_MONTH, 6)
                timeInterval[1] = getTimeInt(calendar)
            }
        }
        return timeInterval
    }

    fun addDays(dateInt: Int, days: Int): Int {
        val calendar = Calendar.getInstance()
        calendar.set(dateInt / 10000, ((dateInt / 100) % 100) - 1, dateInt % 100)
        calendar.add(Calendar.DAY_OF_MONTH, days)
        return getTimeInt(calendar)
    }

    fun getMondayOfWeek(dateInt: Int): Int {
        val calendar = Calendar.getInstance()
        calendar.set(dateInt / 10000, ((dateInt / 100) % 100) - 1, dateInt % 100)
        val dayOfWeek = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
        calendar.add(Calendar.DAY_OF_MONTH, -dayOfWeek)
        return getTimeInt(calendar)
    }

    fun getWeek(year: Int, month: Int): IntArray {
        val calendar = Calendar.getInstance()
        calendar.set(year, month - 1, 1)
        val yc = intArrayOf(-1, 6, 0, 1, 2, 3, 4, 5)
        val days = intArrayOf(-1, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

        val firstDayOfWeek = yc[calendar.get(Calendar.DAY_OF_WEEK)]
        var daysInMonth = days[month]
        if (((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) && month == 2) {
            daysInMonth++
        }
        return intArrayOf(firstDayOfWeek, daysInMonth)
    }

    fun getNextDate(changeDate: Int, flag2: Int): Int {
        val calendar = Calendar.getInstance()
        val y = changeDate / 10000
        val m = ((changeDate / 100) % 100) - 1
        val d = changeDate % 100
        calendar.set(y, m, d)

        if (flag2 == QUEST_TYPE_WEEK) {
            calendar.add(Calendar.WEEK_OF_YEAR, 1)
            return getTimeInt(calendar)
        }
        if (flag2 == QUEST_TYPE_DAY) {
            return getTimeInt()
        }

        var yearVal = y
        var days = getMonthDays(yearVal)
        if (((yearVal % 4 == 0 && yearVal % 100 != 0) || yearVal % 400 == 0)) {
            days = days.copyOf().also { it[2] = 29 }
        }

        var monthVal = m + 1
        if (monthVal > 12) {
            yearVal++
            monthVal = 1
            days = getMonthDays(yearVal)
        }

        var dayVal = d
        if (dayVal > days[monthVal]) {
            monthVal++
            if (monthVal > 12) {
                yearVal++
                monthVal = 1
            }
        }

        calendar.set(yearVal, monthVal, dayVal)
        return getTimeInt(calendar)
    }

    private fun getMonthDays(year: Int): IntArray {
        return if (((year % 4 == 0 && year % 100 != 0) || year % 400 == 0))
            intArrayOf(-1, 31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        else
            intArrayOf(-1, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    }

    private fun trans(num: Int): String {
        val temp = num + 100
        return temp.toString().substring(1, 3)
    }
}
