package com.archite.piecesoflife.ui

import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.archite.piecesoflife.R
import com.archite.piecesoflife.data.UserItem
import com.archite.piecesoflife.data.UserPreferencesRepository
import com.archite.piecesoflife.databinding.ActivityLogEditorBinding
import com.archite.piecesoflife.util.SpriteDef
import com.archite.piecesoflife.util.SpriteLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LogEditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LOG_ID = "logId"
        const val EXTRA_RESULT_ID = "resultId"
        const val NEW_LOG_DEFAULT = -1L
        const val NEW_LOG_QUEST = -2L
        const val RESULT_SAVED = 1
        const val RESULT_DELETED = 2
        fun isNewMode(logId: Long) = logId < 0
    }

    private lateinit var binding: ActivityLogEditorBinding
    private val ioScope = CoroutineScope(Dispatchers.IO)

    private var abbrPairs: List<Pair<String, String>> = emptyList()
    private var phrasePairs: List<Pair<String, String>> = emptyList()
    private var sizeList: List<Int> = emptyList()
    private var colorList: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupSystemBars()
        renderBackgrounds()
        renderSprites()
        setupTitle()
        // 无论新建还是编辑，都通过 loadFromLog 初始化编辑器（新建时传入空内容）
        binding.noteContent.loadFromLog("", "")
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
        /*ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }*/
    }

    private fun renderBackgrounds() {
        binding.toolbarBg.setImageDrawable(SpriteLoader.background48(index = 0, pixelScale = 5))
        binding.bottomBar.background = SpriteLoader.background48(index = 0, pixelScale = 5)
    }

    private fun renderSprites() {
        binding.btnReturn.setImageBitmap(SpriteLoader.button16(17, scale = 5))
        binding.btnContinue.setImageBitmap(SpriteLoader.button16(18, scale = 5))
        SpriteLoader.setButton(binding.btnFCB, SpriteDef.B72x72.RES, SpriteDef.B72x72.frame(4), scale = 5)
        SpriteLoader.setButton(binding.btnTool1, SpriteDef.B24.RES, SpriteDef.B24.frame(6), scale = 5)
        SpriteLoader.setButton(binding.btnTool2, SpriteDef.B24.RES, SpriteDef.B24.frame(14), scale = 5)
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

    private fun setupTitle() {
        val logId = intent.getLongExtra(EXTRA_LOG_ID, NEW_LOG_DEFAULT)
        binding.tvTitle.text = if (isNewMode(logId)) "新建日志" else "编辑日志"
    }

    // ── 编辑器监听器 ──
    private fun setupEditorListener() {
        // 选择变化 → 更新工具栏 toggle / 撤销/重做 状态
        binding.noteContent.setOnSelectionChangedListener { _, _ ->
            updateToolbarState()
        }
        // 触发器检测 → @@ / ## / $$
        binding.noteContent.setOnTriggerDetectedListener { trigger ->
            when (trigger) {
                "@@" -> showAbbrSelector()
                "##" -> showTagSelector()
                "\$\$" -> showPhraseSelector()
            }
        }
    }

    // ── 加载所有数据 ──
    private fun loadAllData() {
        ioScope.launch {
            val userRepo = UserPreferencesRepository(this@LogEditorActivity)
            val items = userRepo.getItems()
            sizeList = userRepo.getSizeList()
            colorList = userRepo.getColorList()

            val attrs = items.filter { it.type == UserItem.TYPE_ATTRIBUTES || it.type == UserItem.TYPE_SKILL }
            val phrases = items.filter { it.type == UserItem.TYPE_PHRASES }
            abbrPairs = attrs.map { "${it.iconEmoji}${it.abbr}" to it.name }
            phrasePairs = phrases.map { it.rule.ifEmpty { it.name } to it.name }

            withContext(Dispatchers.Main) {
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
            }
        }
    }

    // ── 工具栏状态 ──
    private fun updateToolbarState() {
        updateUndoRedoButtons()
        updateToggleButtons()
    }

    private fun updateUndoRedoButtons() {
        val canUndo = binding.noteContent.canUndo()
        val canRedo = binding.noteContent.canRedo()
        if (!canUndo) applyGrayOverlay(binding.btnUndo) else clearGrayOverlay(binding.btnUndo)
        if (!canRedo) applyGrayOverlay(binding.btnRedo) else clearGrayOverlay(binding.btnRedo)
        binding.btnUndo.isEnabled = canUndo
        binding.btnRedo.isEnabled = canRedo
    }

    private fun updateToggleButtons() {
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

    // ── 按钮绑定 ──
    private fun bindClickEvents() {
        binding.btnReturn.setOnClickListener { finish() }
        binding.btnContinue.setOnClickListener { finish() }
        binding.btnFCB.setOnClickListener { finish() }

        // 编辑器工具
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

        // 快速插入
        binding.fastAbbrBtn.setOnClickListener { if (abbrPairs.isNotEmpty()) showAbbrSelector() }
        binding.fastShortBtn.setOnClickListener { if (phrasePairs.isNotEmpty()) showPhraseSelector() }
        binding.fastTagBtn.setOnClickListener { /* 标签功能 Phase 6 实现 */ }
    }

    // ── 字号选择 ──
    private fun showFontSizeSelector() {
        val items = sizeList.map { it.toString() }
        SelectorDialog(this)
            .setTitle("选择字号")
            .setCenteredItems(items)
            .onItemSelected { value, _ ->
                val size = value.toIntOrNull() ?: return@onItemSelected
                binding.noteContent.applyCommand(NoteContentEditText.EDIT_OP_SIZE, size)
                updateToolbarState()
                refocusEditText()
            }
            .setCancelableOutside(true)
            .show()
    }

    // ── 颜色选择 ──
    private fun showFontColorSelector() {
        val items = colorList.map { "▣ $it" }
        SelectorDialog(this)
            .setTitle("选择颜色")
            .setColorItems(items)
            .onItemSelected { value, _ ->
                val colorStr = value.substringAfter("#")
                val color = try {
                    android.graphics.Color.parseColor("#$colorStr")
                } catch (_: Exception) { return@onItemSelected }
                binding.noteContent.applyCommand(NoteContentEditText.EDIT_OP_COLOR, color)
                updateToolbarState()
                refocusEditText()
            }
            .setCancelableOutside(true)
            .show()
    }

    // ── 属性选择器（@@ / 快速插入按钮） ──
    private fun showAbbrSelector() {
        SelectorDialog(this)
            .setTitle("选择属性")
            .setItemsWithRemark(abbrPairs)
            .onItemSelected { value, _ ->
                val abbr = value.replace(Regex("^[\\p{So}\\p{Sk}\\p{Sc}\\p{Mn}]"), "")
                binding.noteContent.insertAtCursor("${abbr}+")
                updateToolbarState()
                refocusEditText()
            }
            .setCancelableOutside(true)
            .show()
    }

    // ── 短语选择器（$$ / 快速插入按钮） ──
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
            .show()
    }

    // ── 标签选择器（## / 快速插入按钮） ──
    // Phase 6 实现 DataStore 标签存取后启用
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
