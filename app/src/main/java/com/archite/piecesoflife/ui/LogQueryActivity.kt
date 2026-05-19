package com.archite.piecesoflife.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.archite.piecesoflife.R
import com.archite.piecesoflife.data.AppDatabase
import com.archite.piecesoflife.data.LogRepository
import com.archite.piecesoflife.databinding.ActivityLogQueryBinding
import com.archite.piecesoflife.util.SpriteDef
import com.archite.piecesoflife.util.SpriteLoader
import com.archite.piecesoflife.util.TimeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LogQueryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogQueryBinding
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private lateinit var logRepo: LogRepository
    private lateinit var calendarVC: CalendarViewController

    private var currentYear = 0
    private var currentMonth = 0
    private var selectedDate = 0
    private var initialDate = 0

    companion object {
        const val EXTRA_EDIT_RESULT = "editResult"
        const val EXTRA_FOCUS_DATE = "focusDate"
        const val EXTRA_KEYWORD = "keyword"
        const val EDIT_STATE_SEARCHING_FINISH = 7

        private const val MIN_YEAR = 1900
        private const val MAX_YEAR = 3000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogQueryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSystemBars()
        renderBackgrounds()
        initData()
        initCalendar()
        renderSprites()
        bindClickEvents()
    }

    private fun setupSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = ContextCompat.getColor(this, R.color.background_top)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.background_bottom)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun renderBackgrounds() {
        binding.toolbarBg.setImageDrawable(SpriteLoader.background48(index = 0, pixelScale = 5))
        binding.bottomSpacer.background = SpriteLoader.background48(index = 0, pixelScale = 5)
        binding.contentBg.setImageDrawable(SpriteLoader.background48(index = 0, pixelScale = 5))
    }

    private fun renderSprites() {
        SpriteLoader.setButton(binding.btnReturn,
            SpriteDef.B16.RES, SpriteDef.B16.frame(17), 4)
        SpriteLoader.setButton(binding.btnYearDec,
            SpriteDef.B32.RES, SpriteDef.B32.frame(16), 5)
        SpriteLoader.setButton(binding.btnYearAdd,
            SpriteDef.B32.RES, SpriteDef.B32.frame(14), 5)
        SpriteLoader.setButton(binding.btnMonthDec,
            SpriteDef.B32.RES, SpriteDef.B32.frame(16), 5)
        SpriteLoader.setButton(binding.btnMonthAdd,
            SpriteDef.B32.RES, SpriteDef.B32.frame(14), 5)
        SpriteLoader.setButton(binding.btnToday,
            SpriteDef.B32.RES, SpriteDef.B32.frame(2), 5)
        SpriteLoader.setButton(binding.btnSearch,
            SpriteDef.B32.RES, SpriteDef.B32.frame(0), 5)
        SpriteLoader.setButton(binding.btnCancel,
            SpriteDef.B72x32.RES, SpriteDef.B72x32.frame(2), 4)
        SpriteLoader.setButton(binding.btnConfirm,
            SpriteDef.B72x32.RES, SpriteDef.B72x32.frame(0), 4)
    }

    private fun initData() {
        logRepo = LogRepository(AppDatabase.getInstance(this).logDao())

        initialDate = intent.getIntExtra(EXTRA_FOCUS_DATE, TimeUtil.getTimeInt())
        selectedDate = initialDate
    }

    private fun initCalendar() {
        currentYear = initialDate / 10000
        currentMonth = (initialDate % 10000) / 100

        calendarVC = CalendarViewController(binding.dateContainer) { dateInt ->
            selectedDate = dateInt
            refreshCalendar()
        }

        refreshCalendar()
    }

    private fun refreshCalendar() {
        binding.tvYear.text = currentYear.toString()
        binding.tvMonth.text = currentMonth.toString()
        binding.tvSelectedDate.text = "${getString(R.string.log_query_selected)}${TimeUtil.dateInt2String(selectedDate)}"

        val (startDate, endDate) = calculateMonthRange(currentYear, currentMonth)
        ioScope.launch {
            val dateSet = logRepo.getLogDateSetInRange(startDate, endDate)
            withContext(Dispatchers.Main) {
                calendarVC.render(currentYear, currentMonth, selectedDate) { date ->
                    date in dateSet
                }
            }
        }
    }

    private fun bindClickEvents() {
        binding.btnReturn.setOnClickListener { finishWithCancel() }
        binding.btnCancel.setOnClickListener { finishWithCancel() }
        binding.btnConfirm.setOnClickListener { finishWithConfirm() }
        binding.btnSearch.setOnClickListener { finishWithSearch() }
        binding.btnToday.setOnClickListener { resetToToday() }

        binding.btnYearDec.setOnClickListener {
            if (currentYear > MIN_YEAR) {
                currentYear--
                refreshCalendar()
            }
        }
        binding.btnYearAdd.setOnClickListener {
            if (currentYear < MAX_YEAR) {
                currentYear++
                refreshCalendar()
            }
        }
        binding.btnMonthDec.setOnClickListener {
            currentMonth--
            if (currentMonth == 0) {
                currentMonth = 12
                if (currentYear > MIN_YEAR) currentYear--
            }
            refreshCalendar()
        }
        binding.btnMonthAdd.setOnClickListener {
            currentMonth++
            if (currentMonth == 13) {
                currentMonth = 1
                if (currentYear < MAX_YEAR) currentYear++
            }
            refreshCalendar()
        }
    }

    private fun resetToToday() {
        val today = TimeUtil.getTimeInt()
        currentYear = today / 10000
        currentMonth = (today % 10000) / 100
        selectedDate = today
        refreshCalendar()
    }

    private fun finishWithCancel() {
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun finishWithConfirm() {
        if (selectedDate == initialDate) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        val intent = Intent().apply {
            putExtra(EXTRA_EDIT_RESULT, EDIT_STATE_SEARCHING_FINISH)
            putExtra(EXTRA_FOCUS_DATE, selectedDate)
            putExtra(EXTRA_KEYWORD, "")
        }
        setResult(RESULT_OK, intent)
        finish()
    }

    private fun finishWithSearch() {
        val keyword = binding.etKeyword.text.toString()
        if (keyword.isEmpty()) return

        val intent = Intent().apply {
            putExtra(EXTRA_EDIT_RESULT, EDIT_STATE_SEARCHING_FINISH)
            putExtra(EXTRA_FOCUS_DATE, selectedDate)
            putExtra(EXTRA_KEYWORD, keyword)
        }
        setResult(RESULT_OK, intent)
        finish()
    }

    private fun calculateMonthRange(year: Int, month: Int): Pair<Int, Int> {
        val daysInMonth = TimeUtil.getWeek(year, month)[1]
        val startDate = year * 10000 + month * 100 + 1
        val endDate = year * 10000 + month * 100 + daysInMonth
        return Pair(startDate, endDate)
    }
}
