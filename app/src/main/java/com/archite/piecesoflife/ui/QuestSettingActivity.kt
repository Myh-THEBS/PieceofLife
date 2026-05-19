package com.archite.piecesoflife.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.archite.piecesoflife.R
import com.archite.piecesoflife.data.QuestType
import com.archite.piecesoflife.databinding.ActivityQuestSettingBinding
import com.archite.piecesoflife.util.SpriteDef
import com.archite.piecesoflife.util.SpriteLoader
import com.archite.piecesoflife.util.TimeUtil
import java.util.Calendar

class QuestSettingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQuestSettingBinding
    private lateinit var calendarVC: CalendarViewController

    private var currentYear = 0
    private var currentMonth = 0
    private var selectedDate = 0
    private var savedDate = 0
    private var isUnlimited = false
    private var isMinusType = false
    private var currentRepeat = -1
    private var initialDate = 0

    companion object {
        const val EXTRA_DEADLINE = "deadline"
        const val EXTRA_QUEST_TYPE_FLAG = "questTypeFlag"
        const val EXTRA_REPEAT_MODE = "repeatMode"

        const val UNLIMITED_DATE = 99990000
        private const val MIN_YEAR = 1900
        private const val MAX_YEAR = 3000

        private val WEEK_NAMES = arrayOf("一", "二", "三", "四", "五", "六", "日")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuestSettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSystemBars()
        renderBackgrounds()
        initData()
        initCalendar()
        renderSprites()
        bindClickEvents()
        updateDeadlineText()
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
        SpriteLoader.setButton(binding.btnCancel,
            SpriteDef.B72x32.RES, SpriteDef.B72x32.frame(2), 4)
        SpriteLoader.setButton(binding.btnConfirm,
            SpriteDef.B72x32.RES, SpriteDef.B72x32.frame(0), 4)

        SpriteLoader.setButton(binding.iconUnlimited,
            SpriteDef.B16.RES, SpriteDef.B16.frame(28), 4)
        SpriteLoader.setButton(binding.iconQuestType,
            SpriteDef.B16.RES, SpriteDef.B16.frame(12), 4)
        SpriteLoader.setButton(binding.iconRepeat,
            SpriteDef.B16.RES, SpriteDef.B16.frame(14), 4)

        updateUnlimitedButton()
        updateQuestTypeButton()
        updateRepeatButtons()
    }

    private fun initData() {
        initialDate = intent.getIntExtra("focusDate", TimeUtil.getTimeInt())
        selectedDate = initialDate
        savedDate = initialDate
        currentRepeat = -1
        isUnlimited = false
        isMinusType = false
    }

    private fun initCalendar() {
        currentYear = initialDate / 10000
        currentMonth = (initialDate % 10000) / 100

        calendarVC = CalendarViewController(binding.dateContainer) { dateInt ->
            if (!isUnlimited) {
                selectedDate = dateInt
                updateDeadlineText()
                refreshCalendar()
            }
        }

        refreshCalendar()
    }

    private fun refreshCalendar() {
        binding.tvYear.text = currentYear.toString()
        binding.tvMonth.text = currentMonth.toString()

        val today = TimeUtil.getTimeInt()
        calendarVC.render(currentYear, currentMonth, if (isUnlimited) 0 else selectedDate) { date ->
            !isUnlimited && date >= today
        }
    }

    private fun updateDeadlineText() {
        binding.tvDeadline.text = when {
            isUnlimited -> getString(R.string.quest_setting_unlimited)
            currentRepeat == QuestType.MONTH -> "每月${selectedDate % 100}日重复"
            currentRepeat == QuestType.WEEK -> "每周${getDayOfWeekChinese(selectedDate)}重复"
            currentRepeat == QuestType.DAY -> "每天重复"
            else -> "${getString(R.string.quest_setting_deadline)}${TimeUtil.dateInt2String(selectedDate)}"
        }
    }

    private fun getDayOfWeekChinese(dateInt: Int): String {
        val calendar = Calendar.getInstance()
        val y = dateInt / 10000
        val m = ((dateInt / 100) % 100) - 1
        val d = dateInt % 100
        calendar.set(y, m, d)
        val yc = intArrayOf(-1, 6, 0, 1, 2, 3, 4, 5)
        return WEEK_NAMES[yc[calendar.get(Calendar.DAY_OF_WEEK)]]
    }

    private fun updateUnlimitedButton() {
        binding.btnUnlimited.setImageBitmap(SpriteLoader.button96x32(if (isUnlimited) 1 else 0))
    }

    private fun updateQuestTypeButton() {
        binding.btnQuestType.setImageBitmap(SpriteLoader.button96x32(if (isMinusType) 3 else 2))
    }

    private fun updateRepeatButtons() {
        binding.btnRepeatMonth.setImageBitmap(
            SpriteLoader.button32(if (currentRepeat == QuestType.MONTH) 7 else 6))
        binding.btnRepeatWeek.setImageBitmap(
            SpriteLoader.button32(if (currentRepeat == QuestType.WEEK) 9 else 8))
        binding.btnRepeatDay.setImageBitmap(
            SpriteLoader.button32(if (currentRepeat == QuestType.DAY) 11 else 10))
    }

    private fun bindClickEvents() {
        binding.btnReturn.setOnClickListener { finishWithCancel() }
        binding.btnCancel.setOnClickListener { finishWithCancel() }
        binding.btnConfirm.setOnClickListener { finishWithConfirm() }

        binding.btnToday.setOnClickListener {
            if (!isUnlimited) {
                val today = TimeUtil.getTimeInt()
                currentYear = today / 10000
                currentMonth = (today % 10000) / 100
                selectedDate = today
                updateDeadlineText()
                refreshCalendar()
            }
        }

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

        binding.btnUnlimited.setOnClickListener {
            isUnlimited = !isUnlimited
            if (isUnlimited) {
                savedDate = selectedDate
            } else {
                selectedDate = savedDate
                currentYear = selectedDate / 10000
                currentMonth = (selectedDate % 10000) / 100
            }
            updateUnlimitedButton()
            updateDeadlineText()
            refreshCalendar()
        }

        binding.btnQuestType.setOnClickListener {
            isMinusType = !isMinusType
            updateQuestTypeButton()
        }

        binding.btnRepeatMonth.setOnClickListener {
            currentRepeat = if (currentRepeat == QuestType.MONTH) -1 else QuestType.MONTH
            updateRepeatButtons()
            updateDeadlineText()
        }
        binding.btnRepeatWeek.setOnClickListener {
            currentRepeat = if (currentRepeat == QuestType.WEEK) -1 else QuestType.WEEK
            updateRepeatButtons()
            updateDeadlineText()
        }
        binding.btnRepeatDay.setOnClickListener {
            currentRepeat = if (currentRepeat == QuestType.DAY) -1 else QuestType.DAY
            updateRepeatButtons()
            updateDeadlineText()
        }
    }

    override fun onResume() {
        super.onResume()
        bindClickEvents()
    }

    private fun finishWithCancel() {
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun finishWithConfirm() {
        val intent = Intent().apply {
            putExtra(EXTRA_DEADLINE, if (isUnlimited) UNLIMITED_DATE else selectedDate)
            putExtra(EXTRA_QUEST_TYPE_FLAG, if (isMinusType) 1 else 0)
            putExtra(EXTRA_REPEAT_MODE, currentRepeat)
        }
        setResult(RESULT_OK, intent)
        finish()
    }
}
