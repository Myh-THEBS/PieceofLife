package com.archite.piecesoflife.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.archite.piecesoflife.R
import com.archite.piecesoflife.data.UserItem
import com.archite.piecesoflife.data.UserPreferencesRepository
import com.archite.piecesoflife.databinding.ActivityItemEditBinding
import com.archite.piecesoflife.util.SpriteDef
import com.archite.piecesoflife.util.SpriteLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ItemEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityItemEditBinding
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private lateinit var userRepo: UserPreferencesRepository

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_TYPE = "type"
        const val EXTRA_INDEX = "index"
        const val EXTRA_NAME = "name"
        const val EXTRA_ABBR = "abbr"
        const val EXTRA_ICON_EMOJI = "iconEmoji"
        const val EXTRA_VALUE = "value"
        const val EXTRA_LEVEL_EXP = "levelExp"
        const val EXTRA_PRIORITY = "priority"
        const val EXTRA_RULE = "rule"

        const val MODE_NEW = "new"
        const val MODE_EDIT = "edit"

        const val RESULT_SAVED = Activity.RESULT_FIRST_USER
        const val RESULT_DELETED = Activity.RESULT_FIRST_USER + 1
    }

    private var mode: String = MODE_NEW
    private var itemType: String = UserItem.TYPE_ATTRIBUTES
    private var editIndex: Int = -1
    private var originalAbbr: String = ""
    private var isEditMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityItemEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSystemBars()
        renderBackgrounds()
        parseIntent()
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

    private fun parseIntent() {
        userRepo = UserPreferencesRepository(this)
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_NEW
        itemType = intent.getStringExtra(EXTRA_TYPE) ?: UserItem.TYPE_ATTRIBUTES
        editIndex = intent.getIntExtra(EXTRA_INDEX, -1)
        isEditMode = mode == MODE_EDIT

        val titlePrefix = if (isEditMode) getString(R.string.item_edit_title) else getString(R.string.item_new_title)
        val typeName = when (itemType) {
            UserItem.TYPE_ATTRIBUTES -> "属性"
            UserItem.TYPE_SKILL -> "技能"
            UserItem.TYPE_PHRASES -> "短语"
            else -> "属性"
        }
        binding.tvTitle.text = "$titlePrefix$typeName"

        //修改输入框内的值
        if (isEditMode) {
            binding.etName.setText(intent.getStringExtra(EXTRA_NAME) ?: "")
            originalAbbr = intent.getStringExtra(EXTRA_ABBR) ?: ""
            binding.etAbbr.setText(originalAbbr)
            binding.etIcon.setText(intent.getStringExtra(EXTRA_ICON_EMOJI) ?: "")
            binding.etValue.setText(intent.getIntExtra(EXTRA_VALUE, 0).toString())
            binding.etLevelExp.setText(intent.getIntExtra(EXTRA_LEVEL_EXP, 1000).toString())
            binding.etPriority.setText(intent.getIntExtra(EXTRA_PRIORITY, 0).toString())
            binding.etRule.setText(intent.getStringExtra(EXTRA_RULE) ?: "")

            binding.etAbbr.isEnabled = false
            binding.etAbbr.background = null
            binding.etAbbr.isFocusable = false
            binding.rowDelete.visibility = View.VISIBLE
        } else {
            binding.etPriority.setText("0")
            binding.etLevelExp.setText("1000")
            binding.rowDelete.visibility = View.GONE
        }

        val isPhrases = itemType == UserItem.TYPE_PHRASES
        val isSkill = itemType == UserItem.TYPE_SKILL
        val isAttrOrSkill = itemType == UserItem.TYPE_ATTRIBUTES || isSkill

        //禁用行
        binding.rowAbbr.visibility = if (isAttrOrSkill) View.VISIBLE else View.GONE
        binding.rowIcon.visibility = if (isAttrOrSkill) View.VISIBLE else View.GONE
        binding.rowValue.visibility = if (isAttrOrSkill) View.VISIBLE else View.GONE
        binding.rowLevelExp.visibility = if (isSkill) View.VISIBLE else View.GONE

        //修改字段名称
        binding.labelName.text = "${typeName}名称"
        binding.labelAbbr.text = "${typeName}简写"
        binding.labelIcon.text = "${typeName}图标"
        binding.labelRule.text = "${typeName}描述"
        binding.labelDelete.text = "删除该${typeName}"
        if (typeName == "属性") {
            binding.labelValue.text = "属性值"
        } else if (isSkill) {
            binding.labelValue.text = "技能经验"
        }

        if (isPhrases) {
            binding.labelRule.text = "短语内容"
        }

    }

    private fun renderSprites() {
        SpriteLoader.setButton(binding.btnReturn, SpriteDef.B16.RES, SpriteDef.B16.frame(17), downFrame = SpriteDef.B16.frame(17), scale = 4)
        SpriteLoader.setButton(binding.btnCancel, SpriteDef.B72x32.RES, SpriteDef.B72x32.frame(2), scale = 4)
        SpriteLoader.setButton(binding.btnConfirm, SpriteDef.B72x32.RES, SpriteDef.B72x32.frame(0), scale = 4)

        binding.iconName.setImageBitmap(SpriteLoader.button16(25, scale = 5))
        binding.iconAbbr.setImageBitmap(SpriteLoader.button16(27, scale = 5))
        binding.iconEmoji.setImageBitmap(SpriteLoader.button16(24, scale = 5))
        binding.iconValue.setImageBitmap(SpriteLoader.button16(26, scale = 5))
        binding.iconLevelExp.setImageBitmap(SpriteLoader.button16(10, scale = 5))
        binding.iconPriority.setImageBitmap(SpriteLoader.button16(23, scale = 5))
        binding.iconRule.setImageBitmap(SpriteLoader.button16(20, scale = 5))

        SpriteLoader.setButton(binding.btnDelete, SpriteDef.B24.RES, SpriteDef.B24.frame(6), scale = 2)
    }

    private fun bindClickEvents() {
        binding.btnReturn.setOnClickListener { finish() }
        binding.btnCancel.setOnClickListener { finish() }

        binding.btnConfirm.setOnClickListener {
            if (validateInput()) {
                saveAndReturn()
            }
        }

        binding.labelName.setOnClickListener { showHint("属性名称", "必填，最多10个字符。") }
        binding.labelAbbr.setOnClickListener { showHint("属性简写", getString(R.string.item_hint_abbr)) }
        binding.labelIcon.setOnClickListener { showHint("属性图标", getString(R.string.item_hint_icon)) }
        binding.labelValue.setOnClickListener { showHint("属性值/技能经验", "属性的当前数值或技能当前累积经验。") }
        binding.labelLevelExp.setOnClickListener { showHint("升级所需经验", getString(R.string.item_hint_level_exp)) }
        binding.labelPriority.setOnClickListener { showHint("优先级", "数值越大，在列表中排序越靠前。") }
        binding.labelRule.setOnClickListener { showHint("属性描述/短语内容", "选填，最长300字。") }

        binding.btnDelete.setOnClickListener {
            PixelDialog(this)
                .setType(PixelDialog.DialogType.WARN)
                .setTitle("删除确认")
                .setMessage("确定要删除此${if (itemType == UserItem.TYPE_PHRASES) "短语" else "属性/技能"}吗？此操作不可撤销。")
                .setConfirmText("确认删除")
                .setCancelText("取消")
                .onConfirm {
                    setResult(RESULT_DELETED, Intent().apply {
                        putExtra(EXTRA_INDEX, editIndex)
                    })
                    finish()
                }
                .show()
        }
    }

    private fun showHint(title: String, message: String) {
        PixelDialog(this)
            .setType(PixelDialog.DialogType.INFO)
            .setTitle(title)
            .setMessage(message)
            .setConfirmText("知道了")
            .show()
    }

    private fun validateInput(): Boolean {
        val name = binding.etName.text.toString().trim()
        if (name.isEmpty()) {
            showHint("输入错误", "属性名称不能为空。")
            return false
        }

        if (itemType != UserItem.TYPE_PHRASES) {
            val abbr = binding.etAbbr.text.toString().trim()
            if (abbr.isEmpty()) {
                showHint("输入错误", "属性简写不能为空。")
                return false
            }
            if (!isEditMode || abbr != originalAbbr) {
                if (!isAbbrUnique(abbr)) {
                    showHint("输入错误", "简写 \"$abbr\" 已被使用，请更换。")
                    return false
                }
            }
        }

        if (itemType == UserItem.TYPE_SKILL) {
            val levelExpStr = binding.etLevelExp.text.toString().trim()
            val levelExp = levelExpStr.toIntOrNull() ?: 0
            if (levelExp <= 0) {
                showHint("输入错误", "升级所需经验必须大于0。")
                return false
            }
        }

        return true
    }

    private fun isAbbrUnique(abbr: String): Boolean {
        var result = true
        val allItems = runBlockingOnIO { userRepo.getItems() }
        for ((i, item) in allItems.withIndex()) {
            if (i != editIndex && item.abbr.equals(abbr, ignoreCase = true)) {
                result = false
                break
            }
        }
        return result
    }

    private fun saveAndReturn() {
        val name = binding.etName.text.toString().trim()
        val abbr = binding.etAbbr.text.toString().trim()
        val iconEmoji = binding.etIcon.text.toString().trim().ifEmpty { "" }
        val value = binding.etValue.text.toString().trim().toIntOrNull() ?: 0
        val levelExp = binding.etLevelExp.text.toString().trim().toIntOrNull() ?: 1000
        val priority = binding.etPriority.text.toString().trim().toIntOrNull() ?: 0
        val rule = binding.etRule.text.toString().trim()

        setResult(RESULT_SAVED, Intent().apply {
            putExtra(EXTRA_INDEX, editIndex)
            putExtra(EXTRA_NAME, name)
            putExtra(EXTRA_TYPE, itemType)
            putExtra(EXTRA_ABBR, abbr)
            putExtra(EXTRA_ICON_EMOJI, iconEmoji)
            putExtra(EXTRA_VALUE, value)
            putExtra(EXTRA_LEVEL_EXP, levelExp)
            putExtra(EXTRA_PRIORITY, priority)
            putExtra(EXTRA_RULE, rule)
        })
        finish()
    }

    private fun <T> runBlockingOnIO(block: suspend () -> T): T {
        var result: T? = null
        val latch = java.util.concurrent.CountDownLatch(1)
        ioScope.launch {
            result = block()
            latch.countDown()
        }
        latch.await()
        return result!!
    }
}
