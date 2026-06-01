package com.archite.piecesoflife.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.SimpleItemAnimator
import com.archite.piecesoflife.R
import com.archite.piecesoflife.data.LogType
import com.archite.piecesoflife.data.UserItem
import com.archite.piecesoflife.databinding.ActivityMainBinding
import com.archite.piecesoflife.util.SpriteDef
import com.archite.piecesoflife.util.SpriteLoader
import com.archite.piecesoflife.util.TimeUtil
import kotlinx.coroutines.launch

data class RefreshOptions(
    val resetFocus: Boolean = false,
    val clearKeyword: Boolean = false,
    val scrollTarget: ScrollTarget = ScrollTarget.NONE,
    val fadeAnimation: Boolean = true,
)

enum class ScrollTarget { NONE, BOTTOM, FIRST_OF_FOCUS_DATE }

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: LogAdapter
    private lateinit var layoutManager: androidx.recyclerview.widget.LinearLayoutManager
    private var isLoading = false
    private var lastEditorIsNewLog = false

    private val logQueryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val newDate = data?.getIntExtra(LogQueryActivity.EXTRA_FOCUS_DATE, 0) ?: 0
            val kw = data?.getStringExtra(LogQueryActivity.EXTRA_KEYWORD) ?: ""
            if (newDate > 0) viewModel.focusDate = newDate
            viewModel.keyword = kw
            refresh(RefreshOptions(scrollTarget = ScrollTarget.FIRST_OF_FOCUS_DATE, fadeAnimation = true))
        }
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh(RefreshOptions(fadeAnimation = false)) }

    private val addonToolLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val editResult = result.data?.getIntExtra(AddonToolActivity.EXTRA_EDIT_RESULT, 0) ?: 0
            val options = when (editResult) {
                AddonToolActivity.RESULT_LOGS_CHANGED -> RefreshOptions(
                    resetFocus = true, clearKeyword = true,
                    scrollTarget = ScrollTarget.BOTTOM, fadeAnimation = true
                )
                AddonToolActivity.RESULT_SETTINGS_CHANGED -> RefreshOptions(fadeAnimation = false)
                else -> RefreshOptions(fadeAnimation = false)
            }
            refresh(options)
        }
    }

    private val logEditorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            if (lastEditorIsNewLog) {
                refresh(RefreshOptions(
                    resetFocus = true, clearKeyword = true,
                    scrollTarget = ScrollTarget.BOTTOM, fadeAnimation = true
                ))
            } else {
                refresh(RefreshOptions(fadeAnimation = false))
            }
        } else if (result.resultCode == LogEditorActivity.RESULT_DELETED) {
            refresh(RefreshOptions(fadeAnimation = false))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSystemBars()
        renderBackgrounds()
        initAvatar()
        initViewModel()
        initRecyclerView()
        renderSprites()
        bindClickEvents()
        runNewDayCheck()
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
        binding.bottomBarBg.setImageDrawable(SpriteLoader.background48(index = 0, pixelScale = 5))
    }

    private fun initAvatar() {
        binding.ivAvatar.setImageDrawable(null)
        binding.ivAvatar.visibility = View.VISIBLE
    }

    private fun initViewModel() {
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        refresh(RefreshOptions(resetFocus = true, clearKeyword = true, scrollTarget = ScrollTarget.BOTTOM))
    }

    private fun renderTopBar(items: List<UserItem>) {
        val skills = items.filter { it.type == UserItem.TYPE_SKILL && it.priority > 0 }
            .sortedByDescending { it.priority }
        val topSkill = skills.firstOrNull()

        if (topSkill != null) {
            val level = topSkill.value / topSkill.levelExp
            binding.tvLevel.text = if (level >= 99) "Lv.99+" else "Lv.$level"
            binding.tvLevel.visibility = View.VISIBLE
        } else {
            binding.tvLevel.visibility = View.GONE
        }

        val attrs = items.filter { it.type == UserItem.TYPE_ATTRIBUTES && it.priority > 0 }
            .sortedByDescending { it.priority }
            .take(3)

        val attrItems = arrayOf(
            Triple(binding.attrItem1, binding.tvAttrIcon1, binding.tvAttrValue1),
            Triple(binding.attrItem2, binding.tvAttrIcon2, binding.tvAttrValue2),
            Triple(binding.attrItem3, binding.tvAttrIcon3, binding.tvAttrValue3),
        )

        for (i in attrItems.indices) {
            val (item, iconTv, valueTv) = attrItems[i]
            if (i < attrs.size) {
                val attr = attrs[i]
                iconTv.text = attr.iconEmoji.ifEmpty { attr.abbr }
                valueTv.text = attr.value.toString()
                item.visibility = View.VISIBLE
            } else {
                item.visibility = View.GONE
            }
        }

        binding.attrContainer.visibility = if (attrs.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun initRecyclerView() {
        layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        adapter = LogAdapter(
            onQuestComplete = { log ->
                lifecycleScope.launch {
                    viewModel.completeQuestSync(log.id, true)
                    refresh(RefreshOptions(fadeAnimation = false))
                }
            },
            onQuestFail = { log ->
                lifecycleScope.launch {
                    viewModel.completeQuestSync(log.id, false)
                    refresh(RefreshOptions(fadeAnimation = false))
                }
            },
            onItemLongClick = { log ->
                when (log.logType) {
                    LogType.HINT -> { }
                    LogType.DEBUG, LogType.ERROR -> {
                        if (viewModel.debugMode) {
                            PixelDialog(this)
                                .setType(PixelDialog.DialogType.WARN)
                                .setTitle("删除调试日志")
                                .setMessage("确定要永久删除这条调试日志吗？")
                                .setButtons(PixelDialog.ButtonMode.DUAL_DELETE_CANCEL)
                                .onConfirm {
                                    lifecycleScope.launch {
                                        viewModel.permanentlyDeleteLog(log.id)
                                        refresh(RefreshOptions(fadeAnimation = false))
                                    }
                                }
                                .show()
                        } else {
                            lifecycleScope.launch {
                                viewModel.permanentlyDeleteLog(log.id)
                                refresh(RefreshOptions(fadeAnimation = false))
                            }
                        }
                    }
                    else -> {
                        lastEditorIsNewLog = false
                        logEditorLauncher.launch(
                            Intent(this, LogEditorActivity::class.java).apply {
                                putExtra(LogEditorActivity.EXTRA_LOG_ID, log.id)
                            }
                        )
                    }
                }
            },
        )

        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = adapter
        (binding.recyclerView.itemAnimator as? SimpleItemAnimator)?.apply {
            addDuration = 0
            removeDuration = 0
            moveDuration = 0
            changeDuration = 0
        }
    }

    private fun renderSprites() {
        val fabUp = SpriteLoader.button72x72(2, scale = 5)
        val fabDown = SpriteLoader.button72x72(3, scale = 5)
        binding.btnNewLog.setImageBitmap(fabUp)
        binding.btnNewLog.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> binding.btnNewLog.setImageBitmap(fabDown)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> binding.btnNewLog.setImageBitmap(fabUp)
            }
            false
        }
        SpriteLoader.setIcon(binding.toolIcon1, 40)
        SpriteLoader.setIcon(binding.toolIcon2, 41)
        SpriteLoader.setIcon(binding.toolIcon3, 42)
        SpriteLoader.setIcon(binding.toolIcon4, 43)
    }

    private fun bindClickEvents() {
        binding.btnTool1.setOnClickListener {
            refresh(RefreshOptions(resetFocus = true, clearKeyword = true, scrollTarget = ScrollTarget.BOTTOM))
        }
        binding.btnTool2.setOnClickListener {
            val intent = Intent(this, LogQueryActivity::class.java)
            intent.putExtra(LogQueryActivity.EXTRA_FOCUS_DATE, viewModel.focusDate)
            logQueryLauncher.launch(intent)
        }
        binding.btnTool3.setOnClickListener {
            addonToolLauncher.launch(Intent(this, AddonToolActivity::class.java))
        }
        binding.btnTool4.setOnClickListener {
            settingsLauncher.launch(Intent(this, AppSettingActivity::class.java))
        }
        binding.btnNewLog.setOnClickListener {
            lastEditorIsNewLog = true
            logEditorLauncher.launch(
                Intent(this, LogEditorActivity::class.java).apply {
                    putExtra(LogEditorActivity.EXTRA_LOG_ID, LogEditorActivity.NEW_LOG_DEFAULT)
                }
            )
        }
        binding.btnNewLog.setOnLongClickListener {
            lastEditorIsNewLog = true
            logEditorLauncher.launch(
                Intent(this, LogEditorActivity::class.java).apply {
                    putExtra(LogEditorActivity.EXTRA_LOG_ID, LogEditorActivity.NEW_LOG_QUEST)
                }
            )
            true
        }
    }

    private fun runNewDayCheck() {
        lifecycleScope.launch {
            val result = viewModel.runNewDayCheck()
            if (result.isNewDay) {
                refresh(RefreshOptions(fadeAnimation = false))
            }
        }
    }

    override fun onResume() {
        super.onResume()
    }

    private fun refresh(options: RefreshOptions) {
        if (isLoading) return
        isLoading = true

        if (options.resetFocus) viewModel.focusDate = TimeUtil.getTimeInt()
        if (options.clearKeyword) viewModel.keyword = ""

        if (options.fadeAnimation && binding.recyclerView.alpha == 1f) {
            binding.recyclerView.animate()
                .alpha(0f).setDuration(150)
                .withEndAction { doRefresh(options) }
                .start()
        } else {
            binding.recyclerView.alpha = 0f
            doRefresh(options)
        }
    }

    private fun doRefresh(options: RefreshOptions) {
        viewModel.refresh { state ->
            binding.tvUsername.text = state.userName
            renderTopBar(state.items)
            adapter.itemAbbrMap = state.itemAbbrMap
            adapter.labelNames = state.labelNames

            val fabParams = binding.btnNewLog.layoutParams as? android.widget.RelativeLayout.LayoutParams
            if (state.leftMode) {
                fabParams?.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_END)
                fabParams?.addRule(android.widget.RelativeLayout.ALIGN_PARENT_START)
            } else {
                fabParams?.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_START)
                fabParams?.addRule(android.widget.RelativeLayout.ALIGN_PARENT_END)
            }
            fabParams?.marginEnd = (if (state.leftMode) 0 else 20 * resources.displayMetrics.density).toInt()
            fabParams?.marginStart = (if (state.leftMode) 20 * resources.displayMetrics.density else 0).toInt()
            binding.btnNewLog.requestLayout()

            if (state.debugMode && viewModel.keyword.isNotEmpty() && options.scrollTarget == ScrollTarget.FIRST_OF_FOCUS_DATE) {
                val summary = buildSearchSummary(state)
                summary?.let {
                    PixelDialog(this)
                        .setType(PixelDialog.DialogType.INFO)
                        .setButtons(PixelDialog.ButtonMode.SINGLE_KNOWN)
                        .setTitle("检索结果")
                        .setMessage(it)
                        .show()
                }
            }

            adapter.submitList(state.logs) {
                binding.recyclerView.post {
                    if (state.logs.isNotEmpty()) {
                        when (options.scrollTarget) {
                            ScrollTarget.BOTTOM -> binding.recyclerView.scrollToPosition(state.logs.size - 1)
                            ScrollTarget.FIRST_OF_FOCUS_DATE -> {
                                val idx = state.logs.indexOfFirst { it.buildDate >= viewModel.focusDate }
                                if (idx >= 0) layoutManager.scrollToPositionWithOffset(idx, 0)
                            }
                            ScrollTarget.NONE -> {}
                        }
                    }

                    binding.recyclerView.animate()
                        .alpha(1f).setDuration(200)
                        .start()

                    isLoading = false
                }
            }
        }
    }

    private fun buildSearchSummary(state: MainUiState): String? {
        val logs = state.logs.filter { it.id > 0 }
        if (logs.isEmpty()) return null
        val dates = logs.map { it.buildDate }.distinct().sorted()
        val startStr = TimeUtil.dateInt2String(dates.first())
        val endStr = TimeUtil.dateInt2String(dates.last())
        return "在 $startStr 至 $endStr 间，检索到 ${logs.size} 条\"${viewModel.keyword}\"相关日志。"
    }
}
