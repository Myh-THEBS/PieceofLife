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

    private var abbrItems: List<String> = emptyList()
    private var phraseItems: List<String> = emptyList()
    private var abbrPairs: List<Pair<String, String>> = emptyList()
    private var phrasePairs: List<Pair<String, String>> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSystemBars()
        renderBackgrounds()
        renderSprites()
        setupTitle()
        loadItemData()
        bindClickEvents()
    }

    private fun setupSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = ContextCompat.getColor(this, R.color.background_top)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.background_bottom)

        /**ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
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

        SpriteLoader.setButton(binding.btnFCB,
            SpriteDef.B72x72.RES, SpriteDef.B72x72.frame(4), scale = 5)

        SpriteLoader.setButton(binding.btnTool1,
            SpriteDef.B24.RES, SpriteDef.B24.frame(6), scale = 5)

        SpriteLoader.setButton(binding.btnTool2,
            SpriteDef.B24.RES, SpriteDef.B24.frame(14), scale = 5)

        val editorTools = listOf(
            binding.btnUndo to 1,
            binding.btnRedo to 2,
            binding.btnBold to 4,
            binding.btnUnderline to 5,
            binding.btnStrikethrough to 16,
            binding.btnFontSize to 3,
            binding.btnFontColor to 6,
        )
        for ((view, index) in editorTools) {
            view.setImageBitmap(SpriteLoader.button16(index, scale = 5))
        }

        val fastTools = listOf(
            binding.fastTagBtn to 29,
            binding.fastShortBtn to 20,
            binding.fastAbbrBtn to 9,
        )
        for ((view, index) in fastTools) {
            view.setImageBitmap(SpriteLoader.button16(index, scale = 5))
        }
    }

    private fun bindClickEvents() {
        binding.btnReturn.setOnClickListener { finish() }
        binding.btnContinue.setOnClickListener { finish() }
        binding.btnFCB.setOnClickListener { finish() }

        binding.fastAbbrBtn.setOnClickListener {
            if (abbrItems.isNotEmpty()) {
                showAbbrSelector()
            }
        }

        binding.fastShortBtn.setOnClickListener {
            if (phraseItems.isNotEmpty()) {
                showPhraseSelector()
            }
        }
    }

    private fun setupTitle() {
        val logId = intent.getLongExtra(EXTRA_LOG_ID, NEW_LOG_DEFAULT)
        binding.tvTitle.text = if (isNewMode(logId)) "新建日志" else "编辑日志"
    }

    private fun loadItemData() {
        ioScope.launch {
            val userRepo = UserPreferencesRepository(this@LogEditorActivity)
            val items = userRepo.getItems()

            val attrs = items.filter { it.type == UserItem.TYPE_ATTRIBUTES || it.type == UserItem.TYPE_SKILL }
            val phrases = items.filter { it.type == UserItem.TYPE_PHRASES }

            val attrDisplayList = attrs.map { "${it.iconEmoji}${it.abbr}" }
            val phraseDisplayList = phrases.map { it.rule.ifEmpty { it.name } }

            val attrPairList = attrs.map { "${it.iconEmoji}${it.abbr}" to it.name }
            val phrasePairList = phrases.map { it.rule.ifEmpty { it.name } to it.name }

            withContext(Dispatchers.Main) {
                abbrItems = attrDisplayList
                phraseItems = phraseDisplayList
                abbrPairs = attrPairList
                phrasePairs = phrasePairList
                updateFastButtons()
            }
        }
    }

    private fun updateFastButtons() {
        if (abbrItems.isEmpty()) {
            binding.fastAbbrBtn.isEnabled = false
            binding.fastAbbrBtn.colorFilter = PorterDuffColorFilter(Color.GRAY, PorterDuff.Mode.SRC_ATOP)
            binding.fastAbbrBtn.alpha = 0.4f
        }
        if (phraseItems.isEmpty()) {
            binding.fastShortBtn.isEnabled = false
            binding.fastShortBtn.colorFilter = PorterDuffColorFilter(Color.GRAY, PorterDuff.Mode.SRC_ATOP)
            binding.fastShortBtn.alpha = 0.4f
        }
    }

    private fun showAbbrSelector() {
        SelectorDialog(this)
            .setTitle("选择属性")
            .setItemsWithRemark(abbrPairs)
            .onItemSelected { value, _ ->
                val abbr = value.replace(Regex("^[\\p{So}\\p{Sk}\\p{Sc}\\p{Mn}]"), "")
                val currentText = binding.etContent.text ?: ""
                val selStart = binding.etContent.selectionStart
                val before = currentText.substring(0, selStart)
                val after = currentText.substring(selStart)
                binding.etContent.setText("${before}${abbr}+${after}")
                binding.etContent.setSelection(selStart + abbr.length + 1)
                refocusEditText()
            }
            .setCancelableOutside(true)
            .show()
    }

    private fun showPhraseSelector() {
        SelectorDialog(this)
            .setTitle("选择短语")
            .setItemsWithRemark(phrasePairs)
            .onItemSelected { value, _ ->
                val currentText = binding.etContent.text ?: ""
                val selStart = binding.etContent.selectionStart
                val before = currentText.substring(0, selStart)
                val after = currentText.substring(selStart)
                binding.etContent.setText("$before$value$after")
                binding.etContent.setSelection(selStart + value.length)
                refocusEditText()
            }
            .setCancelableOutside(true)
            .show()
    }

    private fun refocusEditText() {
        binding.etContent.postDelayed({
            binding.etContent.requestFocus()
            val imm = getSystemService(InputMethodManager::class.java)
            imm?.showSoftInput(binding.etContent, InputMethodManager.SHOW_IMPLICIT)
        }, 150)
    }
}
