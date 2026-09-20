package com.archite.piecesoflife.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.archite.piecesoflife.R
import com.archite.piecesoflife.data.AppDatabase
import com.archite.piecesoflife.data.LogEntity
import com.archite.piecesoflife.data.LogRepository
import com.archite.piecesoflife.data.QuestFlag
import com.archite.piecesoflife.data.UserItem
import com.archite.piecesoflife.data.UserPreferencesRepository
import com.archite.piecesoflife.databinding.ActivityQuestViewBinding
import com.archite.piecesoflife.databinding.ItemQuestDayGroupBinding
import com.archite.piecesoflife.databinding.ItemQuestEntryBinding
import com.archite.piecesoflife.databinding.ItemWeekCellBinding
import com.archite.piecesoflife.util.SpriteDef
import com.archite.piecesoflife.util.SpriteLoader
import com.archite.piecesoflife.util.TimeUtil
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuestViewActivity : AppCompatActivity() {

    companion object {
        private const val ADD_FRAME = 18
        private const val DAY_COUNT = 7
        private const val PANEL_INSET_PX = 16 * 5
        private const val STRIP_BOTTOM_PAD_DP = 8

        private val WEEK_SHORT = arrayOf("一", "二", "三", "四", "五", "六", "日")
        private val WEEK_LONG = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    }

    private lateinit var binding: ActivityQuestViewBinding
    private lateinit var logRepo: LogRepository
    private lateinit var userRepo: UserPreferencesRepository
    private lateinit var viewModel: MainViewModel

    private var weekMonday = 0
    private var selectedDate = 0
    private var hasChanges = false

    private var colorStripSelected = 0
    private var colorStripText = 0

    private var itemAbbrMap: Map<String, String> = emptyMap()
    private var labelNames: Set<String> = emptySet()
    private var pixelFont = true

    private val editorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK || result.resultCode == LogEditorActivity.RESULT_DELETED) {
            hasChanges = true
            refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuestViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        logRepo = LogRepository(AppDatabase.getInstance(this).logDao())
        userRepo = UserPreferencesRepository(this)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        weekMonday = TimeUtil.getMondayOfWeek(TimeUtil.getTimeInt())

        colorStripSelected = ContextCompat.getColor(this, R.color.lightDark)
        colorStripText = ContextCompat.getColor(this, R.color.TEXT_black)

        setupSystemBars()
        renderBackgrounds()
        renderSprites()
        bindClickEvents()
        refresh()
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
        binding.contentPanel.background = SpriteLoader.background48(index = 0, pixelScale = 5)
        binding.bottomBar.background = SpriteLoader.background48(index = 0, pixelScale = 5)
        applyWeekStripPanel()
    }

    private fun applyWeekStripPanel() {
        val bottomPad = (STRIP_BOTTOM_PAD_DP * resources.displayMetrics.density).toInt()
        binding.weekStrip.setPaddingRelative(PANEL_INSET_PX, PANEL_INSET_PX, PANEL_INSET_PX, bottomPad)
    }

    private fun renderSprites() {
        SpriteLoader.setButton(
            binding.btnReturn, SpriteDef.B16.RES, SpriteDef.B16.frame(17),
            downFrame = SpriteDef.B16.frame(17), scale = 5,
        )
        SpriteLoader.setButton(binding.btnPrevWeek, SpriteDef.B32.RES, SpriteDef.B32.frame(26), scale = 4)
        SpriteLoader.setButton(binding.btnThisWeek, SpriteDef.B32.RES, SpriteDef.B32.frame(24), scale = 4)
        SpriteLoader.setButton(binding.btnNextWeek, SpriteDef.B32.RES, SpriteDef.B32.frame(28), scale = 4)
    }

    private fun bindClickEvents() {
        binding.btnReturn.setOnClickListener { finishWithResult() }
        binding.btnPrevWeek.setOnClickListener { shiftWeek(-1) }
        binding.btnNextWeek.setOnClickListener { shiftWeek(1) }
        binding.btnThisWeek.setOnClickListener {
            weekMonday = TimeUtil.getMondayOfWeek(TimeUtil.getTimeInt())
            selectedDate = 0
            refresh(resetScroll = true)
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishWithResult()
            }
        })
    }

    private fun finishWithResult() {
        if (hasChanges) setResult(RESULT_OK)
        finish()
    }

    private fun shiftWeek(delta: Int) {
        weekMonday = TimeUtil.addDays(weekMonday, delta * DAY_COUNT)
        selectedDate = 0
        refresh(resetScroll = true)
    }

    private fun refresh(resetScroll: Boolean = false) {
        val keepScrollY = if (resetScroll) 0 else binding.listScroll.scrollY
        renderWeekStrip()
        lifecycleScope.launch {
            val today = TimeUtil.getTimeInt()
            val quests = withContext(Dispatchers.IO) {
                val items = userRepo.getItems()
                itemAbbrMap = items.filter { it.abbr.isNotEmpty() }
                    .associate { it.abbr to it.iconEmoji }
                labelNames = items.filter { it.type == UserItem.TYPE_LABEL }
                    .map { it.name }.toSet()
                pixelFont = userRepo.getPixelFont()
                logRepo.getQuestsInDateRange(weekMonday, TimeUtil.addDays(weekMonday, DAY_COUNT - 1))
            }
            renderProgress(quests)
            renderList(quests, today)
            binding.listScroll.post { binding.listScroll.scrollTo(0, keepScrollY) }
        }
    }

    private fun renderProgress(quests: List<LogEntity>) {
        binding.weekProgress.setCounts(
            quests.count { QuestFlag.isFinished(it.flag0) },
            quests.count { QuestFlag.isFailed(it.flag0) },
            quests.count { QuestFlag.isUnfinished(it.flag0) },
        )
    }

    private fun renderWeekStrip() {
        binding.weekStrip.removeAllViews()
        for (i in 0 until DAY_COUNT) {
            val date = TimeUtil.addDays(weekMonday, i)
            val cell = ItemWeekCellBinding.inflate(layoutInflater, binding.weekStrip, false)
            cell.tvWeekday.text = WEEK_SHORT[i]
            cell.tvDay.text = (date % 100).toString()
            cell.tvWeekday.setTextColor(colorStripText)
            cell.tvDay.setTextColor(colorStripText)
            if (date == selectedDate) {
                cell.root.setBackgroundColor(colorStripSelected)
            }
            cell.root.setOnClickListener {
                selectedDate = if (selectedDate == date) 0 else date
                refresh(resetScroll = true)
            }
            binding.weekStrip.addView(cell.root)
        }
    }

    private fun renderList(quests: List<LogEntity>, today: Int) {
        val questsByDate = quests.groupBy { it.changeDate }
        val days = if (selectedDate > 0) {
            listOf(selectedDate)
        } else {
            (0 until DAY_COUNT).map { TimeUtil.addDays(weekMonday, it) }
        }

        val todayMonday = TimeUtil.getMondayOfWeek(today)
        val weekPrefix = when (weekMonday) {
            todayMonday -> getString(R.string.quest_week_current)
            TimeUtil.addDays(todayMonday, -DAY_COUNT) -> getString(R.string.quest_week_last)
            TimeUtil.addDays(todayMonday, DAY_COUNT) -> getString(R.string.quest_week_next)
            else -> null
        }

        binding.listContainer.removeAllViews()
        for (date in days) {
            val group = ItemQuestDayGroupBinding.inflate(layoutInflater, binding.listContainer, false)
            group.tvDayDate.text = buildDayLabel(date)
            val dayTag = buildDayTag(date, today, weekPrefix)
            group.tvDayTag.text = dayTag
            group.tvDayTag.visibility = if (dayTag.isEmpty()) View.GONE else View.VISIBLE

            val isPast = date < today
            SpriteLoader.setButton(group.btnDayAdd, SpriteDef.B24.RES, SpriteDef.B24.frame(ADD_FRAME), scale = 4)
            group.btnDayAdd.isEnabled = !isPast
            group.btnDayAdd.alpha = if (isPast) 0.3f else 1f
            if (!isPast) {
                group.btnDayAdd.setOnClickListener { openNewQuest(date) }
            }

            val tasks = (questsByDate[date] ?: emptyList()).sortedWith(
                compareBy<LogEntity>(
                    { if (QuestFlag.isUnfinished(it.flag0)) 0 else 1 },
                    { it.buildDate },
                    { it.buildTime },
                )
            )

            if (tasks.isEmpty()) {
                group.tvDayEmpty.visibility = View.VISIBLE
            } else {
                group.tvDayEmpty.visibility = View.GONE
                for (log in tasks) {
                    val item = ItemQuestEntryBinding.inflate(layoutInflater, group.dayTaskContainer, false)
                    QuestViewHolder(
                        item.root,
                        onQuestComplete = { settled -> settleQuest(settled.id, true) },
                        onQuestFail = { settled -> settleQuest(settled.id, false) },
                        onItemLongClick = { target -> openExistingQuest(target.id) },
                    ).bind(log, itemAbbrMap, labelNames, pixelFont, QuestItemMode.PLAN)
                    group.dayTaskContainer.addView(item.root)
                }
            }
            binding.listContainer.addView(group.root)
        }
    }

    private fun weekdayName(date: Int): String {
        val calendar = Calendar.getInstance()
        calendar.set(date / 10000, ((date / 100) % 100) - 1, date % 100)
        return WEEK_LONG[(calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7]
    }

    private fun buildDayLabel(date: Int): String =
        "${date / 10000}年${(date % 10000) / 100}月${date % 100}日"

    private fun buildDayTag(date: Int, today: Int, weekPrefix: String?): String {
        if (date == today) return getString(R.string.quest_view_today)
        return if (weekPrefix == null) "" else weekPrefix + weekdayName(date)
    }

    private fun openNewQuest(deadline: Int) {
        editorLauncher.launch(Intent(this, LogEditorActivity::class.java).apply {
            putExtra(LogEditorActivity.EXTRA_LOG_ID, LogEditorActivity.NEW_LOG_QUEST)
            putExtra(LogEditorActivity.EXTRA_QUEST_DEADLINE, deadline)
        })
    }

    private fun openExistingQuest(logId: Long) {
        editorLauncher.launch(Intent(this, LogEditorActivity::class.java).apply {
            putExtra(LogEditorActivity.EXTRA_LOG_ID, logId)
        })
    }

    private fun settleQuest(logId: Long, isSuccess: Boolean) {
        lifecycleScope.launch {
            viewModel.completeQuestSync(logId, isSuccess)
            hasChanges = true
            refresh()
        }
    }
}
