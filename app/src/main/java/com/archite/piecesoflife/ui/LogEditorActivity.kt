package com.archite.piecesoflife.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.archite.piecesoflife.R
import com.archite.piecesoflife.data.AppDatabase
import com.archite.piecesoflife.data.ApplyMode
import com.archite.piecesoflife.data.LogEntity
import com.archite.piecesoflife.data.LogItemChange
import com.archite.piecesoflife.data.LogRepository
import com.archite.piecesoflife.data.LogType
import com.archite.piecesoflife.data.QuestFlag
import com.archite.piecesoflife.data.UserItem
import com.archite.piecesoflife.data.UserPreferencesRepository
import com.archite.piecesoflife.databinding.ActivityLogEditorBinding
import com.archite.piecesoflife.util.SpriteDef
import com.archite.piecesoflife.util.SpriteLoader
import com.archite.piecesoflife.util.TimeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.graphics.toColorInt

class LogEditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LOG_ID = "logId"
        const val EXTRA_RESULT_ID = "resultId"
        const val NEW_LOG_DEFAULT = -1L
        const val NEW_LOG_QUEST = -2L
        const val RESULT_SAVED = 1
        const val RESULT_DELETED = 2
        fun isNewMode(logId: Long) = logId < 0

        private val TYPE_NAME_MAP = mapOf(
            LogType.DEFAULT to "日志",
            LogType.QUEST  to "任务",
            LogType.HINT   to "提示",
        )
    }

    private lateinit var binding: ActivityLogEditorBinding
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private lateinit var userRepo: UserPreferencesRepository
    private lateinit var logRepo: LogRepository

    private var abbrPairs: List<Pair<String, String>> = emptyList()
    private var phrasePairs: List<Pair<String, String>> = emptyList()
    private var sizeList: List<Int> = emptyList()
    private var colorList: List<String> = emptyList()
    private var currentLogId = NEW_LOG_DEFAULT
    private var originalLogEntity: LogEntity? = null

    // 任务设置 窗口 intent
    private var questDeadline = 0
    private var questTypeFlag = 0
    private var questRepeat = -1
    private val questSettingLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            questDeadline = data?.getIntExtra(QuestSettingActivity.EXTRA_DEADLINE, 0) ?: 0
            questTypeFlag = data?.getIntExtra(QuestSettingActivity.EXTRA_QUEST_TYPE_FLAG, 0) ?: 0
            questRepeat = data?.getIntExtra(QuestSettingActivity.EXTRA_REPEAT_MODE, -1) ?: -1
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        currentLogId = intent.getLongExtra(EXTRA_LOG_ID, NEW_LOG_DEFAULT)
        userRepo = UserPreferencesRepository(this)
        logRepo = LogRepository(AppDatabase.getInstance(this).logDao())
        setupSystemBars()
        renderBackgrounds()
        renderSprites()
        setupEditorListener()
        loadAllData()
        bindClickEvents()
        updateToolbarState()
        refocusEditText()
    }

    private fun setupSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = ContextCompat.getColor(this, R.color.background_top)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.background_bottom)
    }

    private fun renderBackgrounds() {
        binding.toolbarBg.setImageDrawable(SpriteLoader.background48(index = 0, pixelScale = 5))
        binding.bottomBar.background = SpriteLoader.background48(index = 0, pixelScale = 5)
    }

    private fun renderSprites() {
        binding.btnReturn.setImageBitmap(SpriteLoader.button16(17, scale = 5))
        binding.btnContinue.setImageBitmap(SpriteLoader.button16(18, scale = 5))
        SpriteLoader.setButton(binding.btnFCB, SpriteDef.B72x72.RES, SpriteDef.B72x72.frame(4), scale = 5)

        // btnTool1/btnTool2 的绘制在加载数据时

        val editorIndices = listOf(
            binding.btnUndo to 1, binding.btnRedo to 2, binding.btnBold to 4,
            binding.btnUnderline to 5, binding.btnStrikethrough to 16,
            binding.btnFontSize to 3, binding.btnFontColor to 6,
        )
        for ((v, i) in editorIndices) v.setImageBitmap(SpriteLoader.button16(i, scale = 5))
        val fastIndices = listOf(
            binding.fastTagBtn to 29, binding.fastShortBtn to 20, binding.fastAbbrBtn to 9,
        )
        for ((v, i) in fastIndices) v.setImageBitmap(SpriteLoader.button16(i, scale = 5))
    }


    private fun setupEditorListener() {
        binding.noteContent.setOnSelectionChangedListener { _, _ ->
            updateToolbarState()
        }
        binding.noteContent.setOnTriggerDetectedListener { trigger ->
            when (trigger) {
                "@@" -> showAbbrSelector()
                "##" -> showTagSelector()
                "\$\$" -> showPhraseSelector()
            }
        }
    }

    private fun loadAllData() {
        ioScope.launch {
            val items = userRepo.getItems()
            sizeList = userRepo.getSizeList()
            colorList = userRepo.getColorList()

            val attrs = items.filter { it.type == UserItem.TYPE_ATTRIBUTES || it.type == UserItem.TYPE_SKILL }
            val phrases = items.filter { it.type == UserItem.TYPE_PHRASES }
            abbrPairs = attrs.map { "${it.iconEmoji}${it.name}" to it.abbr }
            phrasePairs = phrases.map { it.rule.ifEmpty { it.name } to it.name }

            var logText = ""
            var logRemark = ""
            var logType = LogType.DEFAULT
            val leftMode = userRepo.getLeftMode()
            if (!isNewMode(currentLogId)) {
                val log = logRepo.getLogById(currentLogId)
                if (log != null) {
                    logText = log.logText
                    logRemark = log.remark
                    logType = log.logType
                    originalLogEntity = log
                    questDeadline = log.changeDate
                    questTypeFlag = if (QuestFlag.isMinusType(log.flag0)) 1 else 0
                    questRepeat = log.flag1
                }
            } else if (currentLogId == NEW_LOG_QUEST) {
                logType = LogType.QUEST
            }

            withContext(Dispatchers.Main) {
                binding.noteContent.loadFromLog(logText, logRemark)
                val prefix = if (isNewMode(currentLogId)) "新建" else "编辑"
                binding.tvTitle.text = "$prefix${TYPE_NAME_MAP[logType] ?: "日志"}"
                binding.fastAbbrBtn.isEnabled = abbrPairs.isNotEmpty()
                binding.fastShortBtn.isEnabled = phrasePairs.isNotEmpty()
                binding.fastTagBtn.isEnabled = false
                binding.btnFontSize.isEnabled = sizeList.isNotEmpty()
                binding.btnFontColor.isEnabled = colorList.isNotEmpty()
                if (abbrPairs.isEmpty()) applyGrayOverlay(binding.fastAbbrBtn)
                if (phrasePairs.isEmpty()) applyGrayOverlay(binding.fastShortBtn)
                applyGrayOverlay(binding.fastTagBtn)
                if (sizeList.isEmpty()) applyGrayOverlay(binding.btnFontSize)
                if (colorList.isEmpty()) applyGrayOverlay(binding.btnFontColor)
                // 绘制顶部工具按钮
                if (!isNewMode(currentLogId)){
                    SpriteLoader.setButton(binding.btnTool1, SpriteDef.B24.RES, SpriteDef.B24.frame(6), scale = 5)
                    binding.btnTool1.isEnabled = true
                } else {
                    binding.btnTool1.isEnabled = false
                }

                val isQuestUnfinished = logType == LogType.QUEST && (isNewMode(currentLogId) ||
                    (originalLogEntity != null && QuestFlag.isUnfinished(originalLogEntity!!.flag0)))
                if (isQuestUnfinished) {
                    SpriteLoader.setButton(binding.btnTool2, SpriteDef.B24.RES, SpriteDef.B24.frame(14), scale = 5)
                    binding.btnTool2.visibility = android.view.View.VISIBLE
                } else if (!isNewMode(currentLogId)) {
                    SpriteLoader.setButton(binding.btnTool2, SpriteDef.B24.RES, SpriteDef.B24.frame(22), scale = 5)
                    binding.btnTool2.visibility = android.view.View.VISIBLE
                } else {
                    binding.btnTool2.visibility = android.view.View.GONE
                }

                val fcbParams = binding.btnFCB.layoutParams as? android.widget.RelativeLayout.LayoutParams
                if (leftMode) {
                    fcbParams?.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_END)
                    fcbParams?.addRule(android.widget.RelativeLayout.ALIGN_PARENT_START)
                } else {
                    fcbParams?.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_START)
                    fcbParams?.addRule(android.widget.RelativeLayout.ALIGN_PARENT_END)
                }
                val margin = (20 * resources.displayMetrics.density).toInt()
                fcbParams?.marginEnd = if (leftMode) 0 else margin
                fcbParams?.marginStart = if (leftMode) margin else 0
                binding.btnFCB.requestLayout()
            }
        }
    }

    private fun updateToolbarState() {
        val canUndo = binding.noteContent.canUndo()
        val canRedo = binding.noteContent.canRedo()
        if (!canUndo) applyGrayOverlay(binding.btnUndo) else clearGrayOverlay(binding.btnUndo)
        if (!canRedo) applyGrayOverlay(binding.btnRedo) else clearGrayOverlay(binding.btnRedo)
        binding.btnUndo.isEnabled = canUndo
        binding.btnRedo.isEnabled = canRedo
        setToggleSelected(binding.btnBold, binding.noteContent.isBold())
        setToggleSelected(binding.btnUnderline, binding.noteContent.isUnderline())
        setToggleSelected(binding.btnStrikethrough, binding.noteContent.isStrikethrough())
    }


    private fun setToggleSelected(view: android.widget.ImageView, selected: Boolean) {
        if (selected) {
            view.setBackgroundResource(R.drawable.button_16x16_bg)
        } else {
            view.background = null
            view.setBackgroundResource(R.drawable.click_background_edit_tool)
        }
    }

    private fun applyGrayOverlay(view: android.widget.ImageView) {
        view.colorFilter = PorterDuffColorFilter(Color.GRAY, PorterDuff.Mode.SRC_ATOP)
        view.alpha = 0.4f
    }

    private fun clearGrayOverlay(view: android.widget.ImageView) {
        view.colorFilter = null
        view.alpha = 1f
    }

    private fun bindClickEvents() {
        binding.btnReturn.setOnClickListener { finish() }
        binding.btnContinue.setOnClickListener { saveAndFinish() }
        binding.btnFCB.setOnClickListener { saveAndFinish() }

        binding.btnTool1.setOnClickListener { onTool1Click() }
        binding.btnTool2.setOnClickListener { onTool2Click() }

        binding.btnUndo.setOnClickListener {
            binding.noteContent.applyCommand(NoteContentEditText.EDIT_OP_UNDO)
            updateToolbarState()
        }
        binding.btnRedo.setOnClickListener {
            binding.noteContent.applyCommand(NoteContentEditText.EDIT_OP_REDO)
            updateToolbarState()
        }
        binding.btnBold.setOnClickListener {
            binding.noteContent.applyCommand(NoteContentEditText.EDIT_OP_BOLD)
            updateToolbarState()
        }
        binding.btnUnderline.setOnClickListener {
            binding.noteContent.applyCommand(NoteContentEditText.EDIT_OP_UNDER)
            updateToolbarState()
        }
        binding.btnStrikethrough.setOnClickListener {
            binding.noteContent.applyCommand(NoteContentEditText.EDIT_OP_STRIKE)
            updateToolbarState()
        }
        binding.btnFontSize.setOnClickListener { showFontSizeSelector() }
        binding.btnFontColor.setOnClickListener { showFontColorSelector() }

        binding.fastAbbrBtn.setOnClickListener { if (abbrPairs.isNotEmpty()) showAbbrSelector() }
        binding.fastShortBtn.setOnClickListener { if (phraseItems.isNotEmpty()) showPhraseSelector() }
        binding.fastTagBtn.setOnClickListener { }
    }

    private val phraseItems: List<String> get() = phrasePairs.map { it.first }

    private fun onTool1Click() {
        if (!isNewMode(currentLogId)) {
            showDeleteConfirm()
        }
    }

    private fun onTool2Click() {
        val logType = originalLogEntity?.logType
            ?: if (currentLogId == NEW_LOG_QUEST) LogType.QUEST else LogType.DEFAULT
        if (logType == LogType.QUEST) {
            val isUnfinished = originalLogEntity == null || QuestFlag.isUnfinished(originalLogEntity!!.flag0)
            if (isUnfinished) openQuestSetting() else openLogInfo()
        } else {
            openLogInfo()
        }
    }

    private fun showDeleteConfirm() {
        val isAlreadyDeleted = originalLogEntity?.isDeleted == true
        if (isAlreadyDeleted) {
            PixelDialog(this)
                .setTitle("永久删除")
                .setMessage("此日志已在回收站中，确定要永久删除吗？此操作不可撤销。")
                .setButtons(PixelDialog.ButtonMode.DUAL_DELETE_CANCEL)
                .onConfirm {
                    ioScope.launch {
                        logRepo.permanentlyDeleteLog(currentLogId)
                        withContext(Dispatchers.Main) {
                            setResult(RESULT_DELETED)
                            finish()
                        }
                    }
                }
                .show()
        } else {
            PixelDialog(this)
                .setTitle("删除日志")
                .setMessage("确定要删除这篇日志吗？删除后可在回收站恢复。")
                .setButtons(PixelDialog.ButtonMode.DUAL_DELETE_CANCEL)
                .onConfirm {
                    ioScope.launch {
                        val items = userRepo.getItems().toMutableList()
                        val deltas = LogItemChange.fromJson(originalLogEntity?.itemsJson ?: "[]")
                        val mode = ApplyMode.fromQuest(
                            originalLogEntity?.logType ?: LogType.DEFAULT,
                            originalLogEntity?.flag0 ?: 0
                        )
                        LogItemChange.apply(deltas, mode.inverse(), items)
                        userRepo.setItems(items)
                        logRepo.softDeleteLog(currentLogId)
                        withContext(Dispatchers.Main) {
                            setResult(RESULT_DELETED)
                            finish()
                        }
                    }
                }
                .show()
        }
    }

    private fun openQuestSetting() {
        val intent = Intent(this, QuestSettingActivity::class.java).apply {
            putExtra(QuestSettingActivity.EXTRA_DEADLINE, questDeadline)
            putExtra(QuestSettingActivity.EXTRA_QUEST_TYPE_FLAG, questTypeFlag)
            putExtra(QuestSettingActivity.EXTRA_REPEAT_MODE, questRepeat)
        }
        questSettingLauncher.launch(intent)
    }

    private fun openLogInfo() {
        // Phase 7 实现 LogInfoActivity
    }

    private fun saveAndFinish() {
        ioScope.launch {
            val now = TimeUtil.getTimeInt()
            val nowTime = TimeUtil.getTimeInt(TimeUtil.TIME_TYPE_SECOND)

            // 1. 从编辑器获取当前文本和 remark
            val logText = binding.noteContent.text?.toString() ?: ""
            val remark = binding.noteContent.getSpanFormatString()

            // 2. 解析属性变更
            val knownAbbrs = abbrPairs.map { it.second }
            val newDeltas = LogItemChange.parseLogText(logText, knownAbbrs)
            val newItemsJson = LogItemChange.toJson(newDeltas)

            // 3. 构建 LogEntity
            val baseLog = originalLogEntity ?: LogEntity(
                logType = if (currentLogId == NEW_LOG_QUEST) LogType.QUEST else LogType.DEFAULT,
                buildDate = now,
                buildTime = nowTime,
                flag1 = -1,
            )

            val questFlag0 = if (baseLog.logType == LogType.QUEST &&
                !QuestFlag.isFinished(baseLog.flag0) &&
                !QuestFlag.isFailed(baseLog.flag0)) {
                questTypeFlag  // 0=DEFAULT_UNFINISHED, 1=MINUS_UNFINISHED
            } else {
                baseLog.flag0
            }

            val toSave = baseLog.copy(
                logText = logText,
                remark = remark,
                itemsJson = newItemsJson,
                changeDate = if (baseLog.logType == LogType.QUEST && questDeadline > 0) questDeadline else now,
                changeTime = nowTime,
                flag0 = questFlag0,
                flag1 = if (baseLog.logType == LogType.QUEST) questRepeat else baseLog.flag1,
                updatedAt = System.currentTimeMillis(),
            )

            // 4. 保存到数据库
            val savedId = logRepo.saveLog(toSave)

            // 5. 属性生效（UNFINISH_QUEST 时 delta=0，无操作）
            val items = userRepo.getItems().toMutableList()
            val oldDeltas = LogItemChange.fromJson(baseLog.itemsJson)
            val applyMode = ApplyMode.fromQuest(baseLog.logType, baseLog.flag0)

            if (!isNewMode(currentLogId)) {
                LogItemChange.apply(oldDeltas, applyMode.inverse(), items)
            }
            LogItemChange.apply(newDeltas, applyMode, items)

            // 6. 保存属性
            userRepo.setItems(items)

            withContext(Dispatchers.Main) {
                setResult(RESULT_OK, intent.apply { putExtra(EXTRA_RESULT_ID, savedId) })
                finish()
            }
        }
    }

    private fun showFontSizeSelector() {
        val items = sizeList.map { it.toString() }
        SelectorDialog(this)
            .setTitle("选择字号")
            .setCenteredItems(items)
            .onItemSelected { value, _ ->
                val size = value.toIntOrNull()
                if (size != null) {
                    binding.noteContent.applyCommand(NoteContentEditText.EDIT_OP_SIZE, size)
                    updateToolbarState()
                    refocusEditText()
                }
            }
            .setCancelableOutside(true)
            .setOnDismissListener { binding.noteContent.resetTrigger() }
            .show()
    }

    private fun showFontColorSelector() {
        val items = colorList.map { "▣ $it" }
        SelectorDialog(this)
            .setTitle("选择颜色")
            .setColorItems(items)
            .onItemSelected { value, _ ->
                val colorStr = value.substringAfter("#")
                val color = runCatching { "#$colorStr".toColorInt() }.getOrNull()
                if (color != null) {
                    binding.noteContent.applyCommand(NoteContentEditText.EDIT_OP_COLOR, color)
                    updateToolbarState()
                    refocusEditText()
                }
            }
            .setCancelableOutside(true)
            .setOnDismissListener { binding.noteContent.resetTrigger() }
            .show()
    }

    private fun showAbbrSelector() {
        SelectorDialog(this)
            .setTitle("选择属性")
            .setItemsWithRemark(abbrPairs)
            .onItemSelected { _, remark ->
                binding.noteContent.insertAtCursor("${remark}+")
                updateToolbarState()
                refocusEditText()
            }
            .setCancelableOutside(true)
            .setOnDismissListener { binding.noteContent.resetTrigger() }
            .show()
    }

    private fun showPhraseSelector() {
        SelectorDialog(this)
            .setTitle("选择短语")
            .setItemsWithRemark(phrasePairs)
            .onItemSelected { value, _ ->
                binding.noteContent.insertAtCursor(value)
                updateToolbarState()
                refocusEditText()
            }
            .setCancelableOutside(true)
            .setOnDismissListener { binding.noteContent.resetTrigger() }
            .show()
    }

    private fun showTagSelector() {
        binding.noteContent.resetTrigger()
    }

    private fun refocusEditText() {
        binding.noteContent.postDelayed({
            binding.noteContent.requestFocus()
            val imm = getSystemService(InputMethodManager::class.java)
            imm?.showSoftInput(binding.noteContent, InputMethodManager.SHOW_IMPLICIT)
        }, 150)
    }
}
