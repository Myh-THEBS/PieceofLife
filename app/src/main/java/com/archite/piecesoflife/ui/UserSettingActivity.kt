package com.archite.piecesoflife.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.archite.piecesoflife.R
import com.archite.piecesoflife.data.AppDatabase
import com.archite.piecesoflife.data.LogEntity
import com.archite.piecesoflife.data.LogRepository
import com.archite.piecesoflife.data.LogType
import com.archite.piecesoflife.data.UserItem
import com.archite.piecesoflife.data.UserPreferencesRepository
import com.archite.piecesoflife.databinding.ActivityUserSettingBinding
import com.archite.piecesoflife.util.SpriteDef
import com.archite.piecesoflife.util.SpriteLoader
import com.archite.piecesoflife.util.TimeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UserSettingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserSettingBinding
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private lateinit var userRepo: UserPreferencesRepository
    private lateinit var logRepo: LogRepository

    private var initialItems: List<UserItem> = emptyList()
    private var currentItems: List<UserItem> = emptyList()
    private var initialUserName: String = ""
    private var currentUserName: String = ""
    private var initialDebug: Boolean = false

    companion object {
        private const val REQ_ATTR = 1001
        private const val REQ_SKILL = 1002
        private const val REQ_PHRASE = 1003
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserSettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSystemBars()
        renderBackgrounds()
        renderSprites()
        initData()
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
        // 返回、取消、确认按钮
        SpriteLoader.setButton(binding.btnReturn, SpriteDef.B16.RES, SpriteDef.B16.frame(17), downFrame = SpriteDef.B16.frame(17), scale = 5)
        SpriteLoader.setButton(binding.btnCancel, SpriteDef.B72x32.RES, SpriteDef.B72x32.frame(2), scale = 5)
        SpriteLoader.setButton(binding.btnConfirm, SpriteDef.B72x32.RES, SpriteDef.B72x32.frame(0), scale = 5)

        // 编辑用户名按钮和用户名图标
        SpriteLoader.setButton(binding.btnEditUserName, SpriteDef.B24.RES, SpriteDef.B24.frame(20), scale = 5)
        binding.iconUserName.setImageBitmap(SpriteLoader.button16(11, scale = 4))

        // 新增属性、技能、短语 按钮
        SpriteLoader.setButton(binding.btnAddAttr, SpriteDef.B24.RES, SpriteDef.B24.frame(18), scale = 5)
        SpriteLoader.setButton(binding.btnAddSkill, SpriteDef.B24.RES, SpriteDef.B24.frame(18), scale = 5)
        SpriteLoader.setButton(binding.btnAddPhrase, SpriteDef.B24.RES, SpriteDef.B24.frame(18), scale = 5)

    }

    // 初始化数据
    private fun initData() {
        userRepo = UserPreferencesRepository(this)
        logRepo = LogRepository(AppDatabase.getInstance(this).logDao())

        ioScope.launch {
            initialDebug = userRepo.getDebugMode()
            initialUserName = userRepo.getUserName()
            currentUserName = initialUserName
            initialItems = userRepo.getItems()
            currentItems = initialItems.toList()

            withContext(Dispatchers.Main) {
                binding.tvUserName.text = currentUserName
                renderAllItems()
            }
        }
    }


    private fun renderAllItems() {
        val attrs = currentItems.filter { it.type == UserItem.TYPE_ATTRIBUTES }.sortedByDescending { it.priority }
        val skills = currentItems.filter { it.type == UserItem.TYPE_SKILL }.sortedByDescending { it.priority }
        val phrases = currentItems.filter { it.type == UserItem.TYPE_PHRASES }.sortedByDescending { it.priority }

        renderItemList(binding.containerAttributes, attrs, UserItem.TYPE_ATTRIBUTES)
        renderItemList(binding.containerSkills, skills, UserItem.TYPE_SKILL)
        renderItemList(binding.containerPhrases, phrases, UserItem.TYPE_PHRASES)

        updateStarMarkers()
    }

    private fun renderItemList(container: LinearLayout, items: List<UserItem>, type: String) {
        container.removeAllViews()
        for ((index, item) in items.withIndex()) {
            val row = createItemRow(item, type, index)
            container.addView(row)
        }
    }

    private fun createItemRow(item: UserItem, type: String, index: Int): LinearLayout {
        val row = LayoutInflater.from(this).inflate(R.layout.item_user_setting_row, null) as LinearLayout
        val tvName = row.findViewById<TextView>(R.id.tvItemName)
        val tvValue = row.findViewById<TextView>(R.id.tvItemValue)
        val btnArrow = row.findViewById<ImageView>(R.id.btnItemArrow)

        val displayEmoji = if (type == UserItem.TYPE_PHRASES) "💬" else item.iconEmoji
        tvName.text = "$displayEmoji ${item.name}"

        when (type) {
            UserItem.TYPE_ATTRIBUTES -> tvValue.text = "${item.value}"
            UserItem.TYPE_SKILL -> {
                val level = item.value / item.levelExp
                val progress = if (item.levelExp > 0) (item.value % item.levelExp).toFloat() / item.levelExp * 100 else 0f
                val levelStr = if (level >= 99) "Lv.99+" else "Lv.$level"
                tvValue.text = "$levelStr  ${"%.1f".format(progress)}%"
            }
            UserItem.TYPE_PHRASES -> tvValue.visibility = android.view.View.GONE
        }

        btnArrow.setImageBitmap(SpriteLoader.button16(18, scale = 5))

        val globalIndex = currentItems.indexOf(item)
        row.setOnClickListener {
            launchItemEdit(type, globalIndex, item)
        }

        return row
    }

    private fun updateStarMarkers() {
        val userChanged = currentUserName != initialUserName
        val attrChanged = itemsChanged(UserItem.TYPE_ATTRIBUTES)
        val skillChanged = itemsChanged(UserItem.TYPE_SKILL)
        val phraseChanged = itemsChanged(UserItem.TYPE_PHRASES)

        binding.tvUserStar.text = if (userChanged) " *" else ""
        binding.tvAttrStar.text = if (attrChanged) " *" else ""
        binding.tvSkillStar.text = if (skillChanged) " *" else ""
        binding.tvPhraseStar.text = if (phraseChanged) " *" else ""
    }

    private fun itemsChanged(type: String): Boolean {
        val initialOfType = initialItems.filter { it.type == type }.sortedByDescending { it.priority }
        val currentOfType = currentItems.filter { it.type == type }.sortedByDescending { it.priority }
        return initialOfType != currentOfType
    }

    private fun hasAnyChanges(): Boolean {
        return currentUserName != initialUserName || currentItems != initialItems
    }

    private fun buildChangeLog(): String {
        val lines = mutableListOf<String>()
        if (currentUserName != initialUserName) {
            lines.add("用户名：$initialUserName -> $currentUserName")
        }

        val currentAttrs = currentItems.filter { it.type != UserItem.TYPE_PHRASES }
        val initialAttrs = initialItems.filter { it.type != UserItem.TYPE_PHRASES }

        for (item in currentAttrs) {
            val key = item.abbr
            val old = initialAttrs.find { it.abbr == key }
            if (old == null) {
                lines.add("新增：${item.abbr}")
            } else if (old != item) {
                lines.add("${item.abbr}：${old.value} -> ${item.value}")
            }
        }

        for (item in initialAttrs) {
            if (currentAttrs.none { it.abbr == item.abbr }) {
                lines.add("删除：${item.abbr}")
            }
        }

        return "APP属性调整：\n" + lines.joinToString("\n")
    }

    private fun buildChangeSummary(): String {
        val lines = mutableListOf<String>()
        if (currentUserName != initialUserName) lines.add(" · 用户名")

        val currentAttrs = currentItems.filter { it.type != UserItem.TYPE_PHRASES }
        val initialAttrs = initialItems.filter { it.type != UserItem.TYPE_PHRASES }

        for (item in currentAttrs) {
            val key = item.abbr
            val old = initialAttrs.find { it.abbr == key }
            if (old == null) {
                lines.add(" · $key（新增）")
            } else if (old != item) {
                lines.add(" · $key")
            }
        }

        for (item in initialAttrs) {
            if (currentAttrs.none { it.abbr == item.abbr }) {
                lines.add(" · ${item.abbr}（删除）")
            }
        }

        val currentPhrases = currentItems.filter { it.type == UserItem.TYPE_PHRASES }
        val initialPhrases = initialItems.filter { it.type == UserItem.TYPE_PHRASES }
        if (currentPhrases != initialPhrases) {
            lines.add(" · 快捷短语变更")
        }

        val showLines = if (lines.size > 3) lines.take(3) + listOf(" · ···") else lines
        return showLines.joinToString("\n")
    }

    private fun doExit() {
        if (!hasAnyChanges()) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        if (initialDebug) {
            PixelDialog(this)
                .setType(PixelDialog.DialogType.WARN)
                .setTitle(getString(R.string.app_setting_unsaved_title))
                .setMessage(getString(R.string.app_setting_unsaved_msg))
                .setConfirmText("确认退出")
                .setCancelText("继续编辑")
                .onConfirm {
                    setResult(RESULT_CANCELED)
                    finish()
                }
                .show()
        } else {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun doConfirm() {
        if (!hasAnyChanges()) {
            setResult(RESULT_OK)
            finish()
            return
        }
        if (initialDebug) {
            val msg = "是否确认变更以下属性？\n" + buildChangeSummary()
            PixelDialog(this)
                .setType(PixelDialog.DialogType.WARN)
                .setTitle(getString(R.string.app_setting_confirm_title))
                .setMessage(msg)
                .setConfirmText("确认变更")
                .setCancelText("取消")
                .onConfirm { applyAndLog() }
                .show()
        } else {
            applyAndLog()
        }
    }

    private fun applyAndLog() {
        ioScope.launch {
            userRepo.setUserName(currentUserName)
            userRepo.setItems(currentItems)

            if (hasAnyChanges()) {
                val now = TimeUtil.getTimeInt()
                val nowTime = TimeUtil.getTimeInt(TimeUtil.TIME_TYPE_SECOND)
                logRepo.saveLog(LogEntity(
                    logType = LogType.DEBUG,
                    logText = buildChangeLog(),
                    buildDate = now,
                    buildTime = nowTime,
                    changeDate = now,
                    changeTime = nowTime,
                    flag1 = 1,
                ))
            }

            withContext(Dispatchers.Main) {
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    // 启动属性编辑活动
    private fun launchItemEdit(type: String, index: Int, item: UserItem) {
        val intent = Intent(this, ItemEditActivity::class.java).apply {
            putExtra(ItemEditActivity.EXTRA_MODE, ItemEditActivity.MODE_EDIT)
            putExtra(ItemEditActivity.EXTRA_TYPE, type)
            putExtra(ItemEditActivity.EXTRA_INDEX, index)
            putExtra(ItemEditActivity.EXTRA_NAME, item.name)
            putExtra(ItemEditActivity.EXTRA_ABBR, item.abbr)
            putExtra(ItemEditActivity.EXTRA_ICON_EMOJI, item.iconEmoji)
            putExtra(ItemEditActivity.EXTRA_VALUE, item.value)
            putExtra(ItemEditActivity.EXTRA_LEVEL_EXP, item.levelExp)
            putExtra(ItemEditActivity.EXTRA_PRIORITY, item.priority)
            putExtra(ItemEditActivity.EXTRA_RULE, item.rule)
        }
        startActivityForResult(intent, when (type) {
            UserItem.TYPE_ATTRIBUTES -> REQ_ATTR
            UserItem.TYPE_SKILL -> REQ_SKILL
            else -> REQ_PHRASE
        })
    }

    // 启动新增属性活动
    private fun launchNewItem(type: String) {
        val intent = Intent(this, ItemEditActivity::class.java).apply {
            putExtra(ItemEditActivity.EXTRA_MODE, ItemEditActivity.MODE_NEW)
            putExtra(ItemEditActivity.EXTRA_TYPE, type)
            putExtra(ItemEditActivity.EXTRA_INDEX, -1)
        }
        startActivityForResult(intent, when (type) {
            UserItem.TYPE_ATTRIBUTES -> REQ_ATTR
            UserItem.TYPE_SKILL -> REQ_SKILL
            else -> REQ_PHRASE
        })
    }

    // 处理属性编辑活动结果
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (data == null) return

        when (resultCode) {
            ItemEditActivity.RESULT_SAVED -> {
                val index = data.getIntExtra(ItemEditActivity.EXTRA_INDEX, -1)
                val newItem = UserItem(
                    name = data.getStringExtra(ItemEditActivity.EXTRA_NAME) ?: "",
                    type = data.getStringExtra(ItemEditActivity.EXTRA_TYPE) ?: UserItem.TYPE_ATTRIBUTES,
                    iconEmoji = data.getStringExtra(ItemEditActivity.EXTRA_ICON_EMOJI) ?: "⚙",
                    value = data.getIntExtra(ItemEditActivity.EXTRA_VALUE, 0),
                    abbr = data.getStringExtra(ItemEditActivity.EXTRA_ABBR) ?: "",
                    rule = data.getStringExtra(ItemEditActivity.EXTRA_RULE) ?: "",
                    levelExp = data.getIntExtra(ItemEditActivity.EXTRA_LEVEL_EXP, 1000),
                    priority = data.getIntExtra(ItemEditActivity.EXTRA_PRIORITY, 0),
                )

                val mutable = currentItems.toMutableList()
                if (index >= 0 && index < mutable.size) {
                    mutable[index] = newItem
                } else {
                    mutable.add(newItem)
                }
                currentItems = mutable
                renderAllItems()
            }
            ItemEditActivity.RESULT_DELETED -> {
                val index = data.getIntExtra(ItemEditActivity.EXTRA_INDEX, -1)
                if (index >= 0 && index < currentItems.size) {
                    val mutable = currentItems.toMutableList()
                    mutable.removeAt(index)
                    currentItems = mutable
                    renderAllItems()
                }
            }
        }
    }

    private fun editUserName() {
        InputDialog(this)
            .setTitle("修改用户名")
            .setInitialText(currentUserName)
            .onConfirm { newName ->
                currentUserName = newName
                binding.tvUserName.text = currentUserName
                updateStarMarkers()
            }
            .show()
    }

    private fun bindClickEvents() {
        binding.btnReturn.setOnClickListener { doExit() }
        binding.btnCancel.setOnClickListener { doExit() }
        binding.btnConfirm.setOnClickListener { doConfirm() }

        binding.btnEditUserName.setOnClickListener { editUserName() }
        binding.btnAddAttr.setOnClickListener { launchNewItem(UserItem.TYPE_ATTRIBUTES) }
        binding.btnAddSkill.setOnClickListener { launchNewItem(UserItem.TYPE_SKILL) }
        binding.btnAddPhrase.setOnClickListener { launchNewItem(UserItem.TYPE_PHRASES) }
    }
}
