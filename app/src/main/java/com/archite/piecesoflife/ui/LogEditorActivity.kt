package com.archite.piecesoflife.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
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
import com.archite.piecesoflife.util.ImageUtil
import com.archite.piecesoflife.util.SpriteDef
import com.archite.piecesoflife.util.SpriteLoader
import com.archite.piecesoflife.util.TimeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.graphics.toColorInt
import java.io.File

enum class LogEditorMode {
    DEFAULT, PICTURE, DOCUMENT
}

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
            LogType.PICTURE to "图片",
            LogType.DOCUMENT to "文档",
        )
    }

    private lateinit var binding: ActivityLogEditorBinding
    private lateinit var userRepo: UserPreferencesRepository
    private lateinit var logRepo: LogRepository

    private var abbrPairs: List<Pair<String, String>> = emptyList()
    private var phrasePairs: List<Pair<String, String>> = emptyList()
    private var sizeList: List<Int> = emptyList()
    private var colorList: List<String> = emptyList()
    private var currentLogId = NEW_LOG_DEFAULT
    private var originalLogEntity: LogEntity? = null
    private var currentMode = LogEditorMode.DEFAULT
    private var currentLogType = LogType.DEFAULT
    private var documentContent: String = ""
    private var isDocPreviewMode = false
    private var isImmersiveMode = false
    private var documentFileName: String = ""
    private val wordCountHandler = Handler(Looper.getMainLooper())
    private val wordCountRunnable = object : Runnable {
        override fun run() {
            updateWordCount()
            wordCountHandler.postDelayed(this, 3000)
        }
    }

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

    private val logInfoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == LogInfoActivity.RESULT_REVERSED) {
            setResult(RESULT_DELETED)
            finish()
            return@registerForActivityResult
        }
        lifecycleScope.launch(Dispatchers.IO) {
            reloadAfterLogInfo()
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

        binding.btnEditToggle.setImageBitmap(SpriteLoader.button16(25, scale = 5))
        binding.btnImmersive.setImageBitmap(SpriteLoader.button16(30, scale = 5))
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
        lifecycleScope.launch(Dispatchers.IO) {
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

            currentLogType = logType
            currentMode = when (logType) {
                LogType.PICTURE -> LogEditorMode.PICTURE
                LogType.DOCUMENT -> LogEditorMode.DOCUMENT
                else -> LogEditorMode.DEFAULT
            }

            if (currentMode == LogEditorMode.DOCUMENT && logRemark.isNotEmpty()) {
                documentFileName = File(logRemark).name
                documentContent = ImageUtil.readDocumentContent(this@LogEditorActivity, documentFileName) ?: ""
            }

            withContext(Dispatchers.Main) {
                val prefix = if (isNewMode(currentLogId)) "新建" else "编辑"
                val typeName = TYPE_NAME_MAP[logType] ?: "日志"
                binding.tvTitle.text = "$prefix$typeName"
                configureForMode()

                if (currentMode == LogEditorMode.DOCUMENT) {
                    binding.etDocumentEditor.setText(documentContent)
                    binding.etDocumentEditor.setSelection(documentContent.length)
                    binding.tvDocPreview.text = ImageUtil.extractPreview(documentContent)
                    isDocPreviewMode = false
                    showDocEditor()
                    updateWordCount()
                } else if (currentMode == LogEditorMode.PICTURE) {
                    if (logRemark.isNotEmpty()) {
                        val imageFileName = File(logRemark).name
                        loadAndDisplayPicture(imageFileName)
                    }
                    binding.noteContent.loadFromLog(logText, logRemark)
                } else {
                    binding.noteContent.loadFromLog(logText, logRemark)
                }

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

                if (!isNewMode(currentLogId)) {
                    SpriteLoader.setButton(binding.btnTool1, SpriteDef.B24.RES, SpriteDef.B24.frame(6), scale = 5)
                    binding.btnTool1.isEnabled = true
                } else {
                    binding.btnTool1.isEnabled = false
                }

                val isQuestUnfinished = logType == LogType.QUEST && (isNewMode(currentLogId) ||
                    (originalLogEntity != null && QuestFlag.isUnfinished(originalLogEntity!!.flag0)))
                if (isQuestUnfinished) {
                    SpriteLoader.setButton(binding.btnTool2, SpriteDef.B24.RES, SpriteDef.B24.frame(14), scale = 5)
                    binding.btnTool2.visibility = View.VISIBLE
                } else if (!isNewMode(currentLogId)) {
                    SpriteLoader.setButton(binding.btnTool2, SpriteDef.B24.RES, SpriteDef.B24.frame(22), scale = 5)
                    binding.btnTool2.visibility = View.VISIBLE
                } else {
                    binding.btnTool2.visibility = View.GONE
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

    private fun configureForMode() {
        when (currentMode) {
            LogEditorMode.DEFAULT -> {
                binding.noteContent.visibility = View.VISIBLE
                binding.ivPicturePreview.visibility = View.GONE
                binding.etDocumentEditor.visibility = View.GONE
                binding.tvDocPreview.visibility = View.GONE
                binding.btnEditToggle.visibility = View.GONE
                binding.btnImmersive.visibility = View.GONE
                binding.editorToolsContainer.visibility = View.VISIBLE
                binding.itemToolContainer.visibility = View.VISIBLE
                binding.tvWordCount.visibility = View.GONE
                binding.btnUndo.visibility = View.VISIBLE
                binding.btnRedo.visibility = View.VISIBLE
                binding.noteContent.hint = "请输入文字："
                resetImmersiveMode()
                stopWordCount()
            }
            LogEditorMode.PICTURE -> {
                binding.noteContent.visibility = View.VISIBLE
                binding.ivPicturePreview.visibility = View.VISIBLE
                binding.etDocumentEditor.visibility = View.GONE
                binding.tvDocPreview.visibility = View.GONE
                binding.btnEditToggle.visibility = View.GONE
                binding.btnImmersive.visibility = View.GONE
                binding.editorToolsContainer.visibility = View.GONE
                binding.itemToolContainer.visibility = View.VISIBLE
                binding.tvWordCount.visibility = View.GONE
                binding.btnUndo.visibility = View.VISIBLE
                binding.btnRedo.visibility = View.VISIBLE
                binding.noteContent.hint = "请输入图片描述"
                resetImmersiveMode()
                stopWordCount()
            }
            LogEditorMode.DOCUMENT -> {
                binding.noteContent.visibility = View.GONE
                binding.ivPicturePreview.visibility = View.GONE
                binding.btnEditToggle.visibility = View.VISIBLE
                binding.btnImmersive.visibility = View.VISIBLE
                binding.editorToolsContainer.visibility = View.GONE
                binding.itemToolContainer.visibility = View.GONE
                binding.tvWordCount.visibility = View.VISIBLE
                binding.btnUndo.visibility = View.GONE
                binding.btnRedo.visibility = View.GONE
                startWordCount()
            }
        }
    }

    private fun updateWordCount() {
        val text = binding.etDocumentEditor.text?.toString() ?: ""
        val count = text.length
        binding.tvWordCount.text = "${count}字"
    }

    private fun startWordCount() {
        updateWordCount()
        wordCountHandler.removeCallbacks(wordCountRunnable)
        wordCountHandler.postDelayed(wordCountRunnable, 3000)
    }

    private fun stopWordCount() {
        wordCountHandler.removeCallbacks(wordCountRunnable)
    }

    private fun showDocEditor() {
        isDocPreviewMode = false
        binding.etDocumentEditor.visibility = View.VISIBLE
        binding.tvDocPreview.visibility = View.GONE
        binding.btnEditToggle.setBackgroundResource(R.drawable.button_16x16_bg)
    }

    private fun showDocPreview() {
        isDocPreviewMode = true
        val content = binding.etDocumentEditor.text?.toString() ?: ""
        binding.tvDocPreview.text = ImageUtil.renderMarkdownToSpannable(content)
        binding.etDocumentEditor.visibility = View.GONE
        binding.tvDocPreview.visibility = View.VISIBLE
        binding.btnEditToggle.setBackgroundResource(R.drawable.click_background_edit_tool)
    }

    private fun toggleDocMode() {
        if (isDocPreviewMode) {
            showDocEditor()
        } else {
            showDocPreview()
        }
    }

    private fun toggleImmersiveMode() {
        isImmersiveMode = !isImmersiveMode
        if (isImmersiveMode) {
            binding.btnImmersive.setBackgroundResource(R.drawable.button_16x16_bg)
            binding.btnFCB.animate().alpha(0f).setDuration(300).withEndAction {
                binding.btnFCB.visibility = View.GONE
            }.start()
        } else {
            binding.btnImmersive.setBackgroundResource(R.drawable.click_background_edit_tool)
            binding.btnFCB.alpha = 0f
            binding.btnFCB.visibility = View.VISIBLE
            binding.btnFCB.animate().alpha(1f).setDuration(300).start()
        }
    }

    private fun resetImmersiveMode() {
        if (isImmersiveMode) {
            isImmersiveMode = false
            binding.btnImmersive.setBackgroundResource(R.drawable.click_background_edit_tool)
            binding.btnFCB.alpha = 0f
            binding.btnFCB.visibility = View.VISIBLE
            binding.btnFCB.animate().alpha(1f).setDuration(200).start()
        }
    }

    private fun loadAndDisplayPicture(imageFileName: String) {
        val bitmap = ImageUtil.loadThumbnail(this, imageFileName)
        if (bitmap != null) {
            val rounded = ImageUtil.drawRoundCornerBitmap(bitmap, 16f)
            binding.ivPicturePreview.setImageBitmap(rounded)
            binding.ivPicturePreview.visibility = View.VISIBLE
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
        binding.btnEditToggle.setOnClickListener { toggleDocMode() }
        binding.btnImmersive.setOnClickListener { toggleImmersiveMode() }

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
        if (currentLogType == LogType.QUEST) {
            val isUnfinished = originalLogEntity == null || QuestFlag.isUnfinished(originalLogEntity!!.flag0)
            if (isUnfinished) {
                openQuestSetting()
            } else {
                checkUnsavedAndNavigate()
            }
        } else {
            checkUnsavedAndNavigate()
        }
    }

    private fun checkUnsavedAndNavigate() {
        val hasChanges = when (currentMode) {
            LogEditorMode.DEFAULT -> {
                val currentText = binding.noteContent.text?.toString() ?: ""
                currentText != (originalLogEntity?.logText ?: "")
            }
            LogEditorMode.PICTURE -> {
                val currentText = binding.noteContent.text?.toString() ?: ""
                currentText != (originalLogEntity?.logText ?: "")
            }
            LogEditorMode.DOCUMENT -> {
                val currentDocText = binding.etDocumentEditor.text?.toString() ?: ""
                currentDocText != documentContent
            }
        }
        if (hasChanges) {
            PixelDialog(this)
                .setType(PixelDialog.DialogType.INFO)
                .setTitle(getString(R.string.log_info_editor_unsaved_title))
                .setMessage(getString(R.string.log_info_editor_unsaved_msg))
                .setButtons(PixelDialog.ButtonMode.DUAL_CONFIRM_CANCEL)
                .onConfirm {
                    lifecycleScope.launch(Dispatchers.IO) { saveDocContent(); openLogInfo() }
                }
                .show()
        } else {
            openLogInfo()
        }
    }

    private fun saveDocContent() {
        if (currentMode == LogEditorMode.DOCUMENT && documentFileName.isNotEmpty()) {
            val content = binding.etDocumentEditor.text?.toString() ?: ""
            ImageUtil.writeDocumentContent(this, documentFileName, content)
            documentContent = content
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
                    lifecycleScope.launch(Dispatchers.IO) {
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
                    lifecycleScope.launch(Dispatchers.IO) {
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
        val intent = Intent(this, LogInfoActivity::class.java).apply {
            putExtra(LogInfoActivity.EXTRA_LOG_ID, currentLogId)
        }
        logInfoLauncher.launch(intent)
    }

    private suspend fun reloadAfterLogInfo() {
        val log = logRepo.getLogById(currentLogId) ?: return
        originalLogEntity = log
        withContext(Dispatchers.Main) {
            when (currentMode) {
                LogEditorMode.DOCUMENT -> {
                    if (log.remark.isNotEmpty()) {
                        documentFileName = File(log.remark).name
                        documentContent = ImageUtil.readDocumentContent(this@LogEditorActivity, documentFileName) ?: ""
                        binding.etDocumentEditor.setText(documentContent)
                        binding.etDocumentEditor.setSelection(documentContent.length)
                        binding.tvDocPreview.text = ImageUtil.extractPreview(documentContent)
                        showDocEditor()
                    }
                }
                LogEditorMode.PICTURE -> {
                    binding.noteContent.loadFromLog(log.logText, log.remark)
                    if (log.remark.isNotEmpty()) {
                        val imageFileName = File(log.remark).name
                        loadAndDisplayPicture(imageFileName)
                    }
                }
                LogEditorMode.DEFAULT -> {
                    binding.noteContent.loadFromLog(log.logText, log.remark)
                }
            }
        }
    }

    private fun saveAndFinish() {
        lifecycleScope.launch(Dispatchers.IO) {
            val now = TimeUtil.getTimeInt()
            val nowTime = TimeUtil.getTimeInt(TimeUtil.TIME_TYPE_SECOND)

            var logText = ""
            var remark = ""

            when (currentMode) {
                LogEditorMode.DOCUMENT -> {
                    saveDocContent()
                    if (documentFileName.isNotEmpty()) {
                        val preview = ImageUtil.extractPreview(documentContent)
                        logText = preview
                        remark = "documents/$documentFileName"
                    }
                }
                LogEditorMode.PICTURE -> {
                    logText = binding.noteContent.text?.toString() ?: ""
                    remark = originalLogEntity?.remark ?: ""
                }
                LogEditorMode.DEFAULT -> {
                    logText = binding.noteContent.text?.toString() ?: ""
                    remark = binding.noteContent.getSpanFormatString()
                }
            }

            val knownAbbrs = abbrPairs.map { it.second }
            val newDeltas = LogItemChange.parseLogText(logText, knownAbbrs)
            val newItemsJson = LogItemChange.toJson(newDeltas)

            val baseLog = originalLogEntity ?: LogEntity(
                logType = currentLogType,
                buildDate = now,
                buildTime = nowTime,
                flag1 = -1,
            )

            val questFlag0 = if (baseLog.logType == LogType.QUEST &&
                !QuestFlag.isFinished(baseLog.flag0) &&
                !QuestFlag.isFailed(baseLog.flag0)) {
                questTypeFlag
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

            val savedId = logRepo.saveLog(toSave)

            if (currentMode != LogEditorMode.DOCUMENT) {
                val items = userRepo.getItems().toMutableList()
                val oldDeltas = LogItemChange.fromJson(baseLog.itemsJson)
                val applyMode = ApplyMode.fromQuest(baseLog.logType, baseLog.flag0)
                if (!isNewMode(currentLogId)) {
                    LogItemChange.apply(oldDeltas, applyMode.inverse(), items)
                }
                LogItemChange.apply(newDeltas, applyMode, items)
                userRepo.setItems(items)
            }

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

    override fun onDestroy() {
        super.onDestroy()
        wordCountHandler.removeCallbacks(wordCountRunnable)
    }
}
