package com.archite.piecesoflife.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.archite.piecesoflife.R
import com.archite.piecesoflife.data.AppDatabase
import com.archite.piecesoflife.data.LogEntity
import com.archite.piecesoflife.data.LogRepository
import com.archite.piecesoflife.data.LogType
import com.archite.piecesoflife.databinding.ActivityDataStatsBinding
import com.archite.piecesoflife.util.SpriteDef
import com.archite.piecesoflife.util.SpriteLoader
import com.archite.piecesoflife.util.TimeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DataStatsActivity : AppCompatActivity() {

    companion object {
        private const val MIN_YEAR = 2020
        private const val MAX_YEAR = 2099
    }

    private lateinit var binding: ActivityDataStatsBinding
    private lateinit var logRepo: LogRepository
    private lateinit var calendarVC: CalendarHeatmapViewController

    private var currentYear = 2026
    private var currentMonth = 1
    private var dayCountMap = emptyMap<Int, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDataStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        logRepo = LogRepository(AppDatabase.getInstance(this).logDao())
        calendarVC = CalendarHeatmapViewController(binding.calendarContainer)

        val today = TimeUtil.getTimeInt()
        currentYear = today / 10000
        currentMonth = (today % 10000) / 100

        setupSystemBars()
        renderBackgrounds()
        renderSprites()
        bindClickEvents()
        refreshStats()
    }

    private fun setupSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = ContextCompat.getColor(this, R.color.background_top)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.background_bottom)
    }

    private fun renderBackgrounds() {
        binding.toolbarBg.setImageDrawable(SpriteLoader.background48(index = 0, pixelScale = 5))
        binding.bottomSpacer.background = SpriteLoader.background48(index = 0, pixelScale = 5)
        binding.contentBg.setImageDrawable(SpriteLoader.background48(index = 0, pixelScale = 5))
    }

    private fun renderSprites() {
        SpriteLoader.setButton(binding.btnReturn, SpriteDef.B16.RES, SpriteDef.B16.frame(17), downFrame = SpriteDef.B16.frame(17), scale = 5)
        SpriteLoader.setButton(binding.btnCancel, SpriteDef.B72x32.RES, SpriteDef.B72x32.frame(2), scale = 5)
        SpriteLoader.setButton(binding.btnConfirm, SpriteDef.B72x32.RES, SpriteDef.B72x32.frame(0), scale = 5)
        SpriteLoader.setButton(binding.btnYearDec, SpriteDef.B32.RES, SpriteDef.B32.frame(16), scale = 5)
        SpriteLoader.setButton(binding.btnYearAdd, SpriteDef.B32.RES, SpriteDef.B32.frame(14), scale = 5)
        SpriteLoader.setButton(binding.btnMonthDec, SpriteDef.B32.RES, SpriteDef.B32.frame(16), scale = 5)
        SpriteLoader.setButton(binding.btnMonthAdd, SpriteDef.B32.RES, SpriteDef.B32.frame(14), scale = 5)
    }

    private fun bindClickEvents() {
        binding.btnReturn.setOnClickListener { finish() }
        binding.btnCancel.setOnClickListener { finish() }
        binding.btnConfirm.setOnClickListener { finish() }

        binding.btnYearDec.setOnClickListener {
            if (currentYear > MIN_YEAR) { currentYear--; refreshStats() }
        }
        binding.btnYearAdd.setOnClickListener {
            if (currentYear < MAX_YEAR) { currentYear++; refreshStats() }
        }
        binding.btnMonthDec.setOnClickListener {
            currentMonth--
            if (currentMonth == 0) { currentMonth = 12; if (currentYear > MIN_YEAR) currentYear-- }
            refreshStats()
        }
        binding.btnMonthAdd.setOnClickListener {
            currentMonth++
            if (currentMonth == 13) { currentMonth = 1; if (currentYear < MAX_YEAR) currentYear++ }
            refreshStats()
        }
    }

    private fun refreshStats() {
        binding.tvYear.text = "${currentYear}年"
        binding.tvMonth.text = "${currentMonth}月"

        lifecycleScope.launch {
            val (startDate, endDate) = calculateMonthRange(currentYear, currentMonth)

            val logs = logRepo.getLogsInDateRange(startDate, endDate)
                .filter { it.logType != LogType.HINT && it.logType != LogType.DEBUG && it.logType != LogType.ERROR }

            dayCountMap = logs.groupBy { it.buildDate }.mapValues { it.value.size }

            withContext(Dispatchers.Main) {
                calendarVC.render(currentYear, currentMonth) { dateInt ->
                    dayCountMap[dateInt] ?: 0
                }
                binding.tvStatsInfo.text = buildStatsString(logs)
            }
        }
    }

    private fun buildStatsString(logs: List<LogEntity>): String {
        val totalCount = logs.size
        val nonDocLogs = logs.filter { it.logType != LogType.DOCUMENT }
        val totalWords = nonDocLogs.sumOf { it.logText.length }
        val dayCounts = logs.groupBy { it.buildDate }
        val maxDayCount = dayCounts.maxOfOrNull { it.value.size } ?: 0
        val maxDayDate = dayCounts.filter { it.value.size == maxDayCount }.keys.firstOrNull() ?: 0
        val dayWords = nonDocLogs.groupBy { it.buildDate }
        val maxDayWords = dayWords.maxOfOrNull { it.value.sumOf { l -> l.logText.length } } ?: 0
        val maxWordsDate = dayWords.filter { it.value.sumOf { l -> l.logText.length } == maxDayWords }.keys.firstOrNull() ?: 0
        val maxSingleWords = nonDocLogs.maxOfOrNull { it.logText.length } ?: 0
        val maxSingleDate = nonDocLogs.firstOrNull { it.logText.length == maxSingleWords }?.buildDate ?: 0

        val sb = StringBuilder()
        sb.append(getString(R.string.data_stats_month_count)).append(" $totalCount 条\n")
        sb.append(getString(R.string.data_stats_month_words)).append(" $totalWords 字\n")
        sb.append(getString(R.string.data_stats_max_day)).append(" $maxDayCount 条（${TimeUtil.dateInt2String(maxDayDate)}）\n")
        sb.append(getString(R.string.data_stats_max_words)).append(" $maxDayWords 字（${TimeUtil.dateInt2String(maxWordsDate)}）\n")
        sb.append(getString(R.string.data_stats_max_single)).append(" $maxSingleWords 字（${TimeUtil.dateInt2String(maxSingleDate)}）\n")

        return sb.toString()
    }

    private fun calculateMonthRange(year: Int, month: Int): Pair<Int, Int> {
        val startDate = year * 10000 + month * 100 + 1
        val daysInMonth = TimeUtil.getWeek(year, month)[1]
        val endDate = year * 10000 + month * 100 + daysInMonth
        return startDate to endDate
    }
}
