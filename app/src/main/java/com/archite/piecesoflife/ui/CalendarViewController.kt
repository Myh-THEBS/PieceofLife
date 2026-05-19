package com.archite.piecesoflife.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.archite.piecesoflife.R
import com.archite.piecesoflife.util.TimeUtil

class CalendarViewController(
    private val dateContainer: LinearLayout,
    private val onDateSelected: (dateInt: Int) -> Unit,
) {

    private val cells = arrayOfNulls<TextView>(42)
    private val dayNumbers = IntArray(42)
    private var renderYear = 0
    private var renderMonth = 0
    private var lastFocusCell = -1
    private val font: Typeface? by lazy {
        ResourcesCompat.getFont(dateContainer.context, R.font.fonsung)
    }

    /**
     * 渲染指定年月的日历网格。
     * @param year      年份，如 2026
     * @param month     月份，1-12
     * @param focusDate 当前选中日期 YYYYMMDD（0=无选中）
     * @param isEnabledCheck 判断某日 YYYYMMDD 是否可点击
     */
    fun render(year: Int, month: Int, focusDate: Int, isEnabledCheck: (Int) -> Boolean) {
        renderYear = year
        renderMonth = month

        if (dateContainer.childCount == 0) {
            buildCells()
        }

        val focusYear = focusDate / 10000
        val focusMonth = (focusDate % 10000) / 100
        val focusDay = focusDate % 100

        val weekInfo = TimeUtil.getWeek(year, month)
        val firstDayOfWeek = weekInfo[0]
        val daysInMonth = weekInfo[1]

        for (i in 0 until 42) {
            dayNumbers[i] = 0
        }
        for (d in 0 until daysInMonth) {
            dayNumbers[firstDayOfWeek + d] = d + 1
        }

        if (lastFocusCell >= 0 && lastFocusCell < 42) {
            cells[lastFocusCell]?.setBackgroundColor(Color.TRANSPARENT)
        }
        lastFocusCell = -1

        for (i in 0 until 42) {
            val tv = cells[i] ?: continue
            val day = dayNumbers[i]
            if (day == 0) {
                tv.text = " "
                tv.isClickable = false
                tv.setTextColor(Color.LTGRAY)
            } else {
                val dateInt = year * 10000 + month * 100 + day
                tv.text = day.toString()
                if (isEnabledCheck(dateInt)) {
                    tv.setTextColor(Color.BLACK)
                    tv.isClickable = true
                } else {
                    tv.setTextColor(Color.LTGRAY)
                    tv.isClickable = false
                }
                if (focusYear == year && focusMonth == month && focusDay == day) {
                    tv.setBackgroundColor(Color.parseColor("#E4E4E4"))
                    lastFocusCell = i
                }
            }
        }
    }

    private fun buildCells() {
        dateContainer.removeAllViews()

        val headerRow = LinearLayout(dateContainer.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp32(dateContainer.context)
            )
            orientation = LinearLayout.HORIZONTAL
        }
        val weekLabels = arrayOf("一", "二", "三", "四", "五", "六", "日")
        for (i in 0 until 7) {
            headerRow.addView(TextView(dateContainer.context).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp32(dateContainer.context), 1f)
                gravity = Gravity.CENTER
                text = weekLabels[i]
                textSize = 24f
                setTextColor(Color.BLACK)
                setTypeface(font, Typeface.BOLD)
            })
        }
        dateContainer.addView(headerRow)

        for (row in 0 until 6) {
            val weekRow = LinearLayout(dateContainer.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp32(dateContainer.context)
                )
                orientation = LinearLayout.HORIZONTAL
            }
            for (col in 0 until 7) {
                val index = row * 7 + col
                val cell = TextView(dateContainer.context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp32(dateContainer.context), 1f)
                    gravity = Gravity.CENTER
                    textSize = 24f
                    setTextColor(Color.LTGRAY)
                    setTypeface(font, Typeface.BOLD)
                    setOnClickListener {
                        val day = dayNumbers[index]
                        if (day != 0) {
                            val dateInt = renderYear * 10000 + renderMonth * 100 + day
                            onDateSelected(dateInt)
                        }
                    }
                }
                cells[index] = cell
                weekRow.addView(cell)
            }
            dateContainer.addView(weekRow)
        }
    }

    private fun dp32(context: Context): Int {
        val density = context.resources.displayMetrics.density
        return (32 * density + 0.5f).toInt()
    }

}
