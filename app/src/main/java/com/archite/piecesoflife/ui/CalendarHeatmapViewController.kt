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

class CalendarHeatmapViewController(
    private val dateContainer: LinearLayout,
) {

    private val cells = arrayOfNulls<TextView>(42)
    private val dayNumbers = IntArray(42)
    private var renderYear = 0
    private var renderMonth = 0
    private val font: Typeface? by lazy {
        ResourcesCompat.getFont(dateContainer.context, R.font.fonsung)
    }

    fun render(year: Int, month: Int, countProvider: (Int) -> Int) {
        renderYear = year
        renderMonth = month

        if (dateContainer.childCount == 0) {
            buildCells()
        }

        val weekInfo = TimeUtil.getWeek(year, month)
        val firstDayOfWeek = weekInfo[0]
        val daysInMonth = weekInfo[1]

        for (i in 0 until 42) {
            dayNumbers[i] = 0
        }
        for (d in 0 until daysInMonth) {
            dayNumbers[firstDayOfWeek + d] = d + 1
        }

        for (i in 0 until 42) {
            val tv = cells[i] ?: continue
            val day = dayNumbers[i]
            if (day == 0) {
                tv.text = " "
                tv.background = null
            } else {
                val dateInt = year * 10000 + month * 100 + day
                val count = countProvider(dateInt)
                tv.text = day.toString()
                tv.background = if (count > 0) {
                    val level = ((count.coerceAtMost(30) - 1) / 29f).coerceIn(0f, 1f)
                    val alpha = (0.12f + level * 0.78f).coerceIn(0f, 1f)
                    val bg = android.graphics.drawable.ColorDrawable(
                        Color.argb((alpha * 255).toInt(), 76, 175, 80)
                    )
                    bg
                } else {
                    android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
                }
                tv.setTextColor(if (count > 0) Color.BLACK else Color.LTGRAY)
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
