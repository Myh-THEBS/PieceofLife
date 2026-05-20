package com.archite.piecesoflife.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.archite.piecesoflife.R
import com.archite.piecesoflife.data.AppDatabase
import com.archite.piecesoflife.data.LogRepository
import com.archite.piecesoflife.data.UserItem
import com.archite.piecesoflife.data.UserPreferencesRepository
import com.archite.piecesoflife.databinding.ActivityMainBinding
import com.archite.piecesoflife.util.SpriteDef
import com.archite.piecesoflife.util.SpriteLoader
import com.archite.piecesoflife.util.TimeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private lateinit var userRepo: UserPreferencesRepository
    private lateinit var logRepo: LogRepository

    private val logQueryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val focusDate = data?.getIntExtra(LogQueryActivity.EXTRA_FOCUS_DATE, 0) ?: 0
            val keyword = data?.getStringExtra(LogQueryActivity.EXTRA_KEYWORD) ?: ""
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSystemBars()
        renderBackgrounds()
        initAvatar()
        initData()
        initRecyclerView()
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
        binding.bottomBarBg.setImageDrawable(SpriteLoader.background48(index = 0, pixelScale = 5))
    }

    private fun initAvatar() {
        binding.ivAvatar.setImageDrawable(null)
        binding.ivAvatar.setBackgroundColor(Color.WHITE)
        binding.ivAvatar.visibility = View.VISIBLE
    }

    private fun initData() {
        userRepo = UserPreferencesRepository(this)
        logRepo = LogRepository(AppDatabase.getInstance(this).logDao())

        ioScope.launch {
            val items = userRepo.getItems()
            val userName = userRepo.getUserName()
            withContext(Dispatchers.Main) {
                binding.tvUsername.text = userName
                renderTopBar(items)
            }
        }
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
                val iconText = attr.iconEmoji.ifEmpty { attr.abbr }
                iconTv.text = iconText
                valueTv.text = attr.value.toString()
                item.visibility = View.VISIBLE
            } else {
                item.visibility = View.GONE
            }
        }

        binding.attrContainer.visibility = if (attrs.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun initRecyclerView() {
        binding.recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
    }

    private fun renderSprites() {
        SpriteLoader.setButton(binding.btnNewLog,
            SpriteDef.B72x72.RES, SpriteDef.B72x72.frame(2), 5)

        SpriteLoader.setIcon(binding.toolIcon1, 40)
        SpriteLoader.setIcon(binding.toolIcon2, 41)
        SpriteLoader.setIcon(binding.toolIcon3, 42)
        SpriteLoader.setIcon(binding.toolIcon4, 43)
    }

    private fun bindClickEvents() {
        binding.btnTool1.setOnClickListener { resetToToday() }

        binding.btnTool2.setOnClickListener {
            val intent = Intent(this, LogQueryActivity::class.java)
            intent.putExtra(LogQueryActivity.EXTRA_FOCUS_DATE, TimeUtil.getTimeInt())
            logQueryLauncher.launch(intent)
        }

        binding.btnTool3.setOnClickListener {
            startActivity(Intent(this, AddonToolActivity::class.java))
        }

        binding.btnTool4.setOnClickListener {
            startActivity(Intent(this, AppSettingActivity::class.java))
        }

        binding.btnNewLog.setOnClickListener {
            // 第四阶段实现：打开日志编辑器
        }
    }

    private fun resetToToday() {
        // 第三阶段核心功能：重置 focusDate 并刷新列表
    }
}
